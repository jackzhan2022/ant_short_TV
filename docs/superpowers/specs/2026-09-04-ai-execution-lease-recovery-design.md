# AI 长任务执行租约与逐集恢复设计

## 背景

项目 27 的角色、场景和道具逐集识别共有 58 个单元。统一执行任务的租约为 10 分钟，逐集处理超过 10 分钟后被调度器回收。旧执行仍在运行，其后续工具调用因 attempt 已失效而失败，并可能把父分析任务写成 `FAILED`；调度器同时已经启动新 attempt，导致页面状态与真实执行状态不一致。

当前代码已经具备三项可复用能力：`AiExecutionClaimService.heartbeat` 能续签租约；执行 attempt 和 claim token 能提供 fencing；逐集快照能把中断的 `RUNNING` 单元回收为 `STALE`，并只重新运行 `FAILED`、`STALE` 和 `PENDING` 单元。

## 目标

- 所有统一执行的长任务在正常运行期间持续续签租约，不再因固定 10 分钟边界被误回收。
- 失去执行权的旧 attempt 不能覆盖新 attempt 的父任务、阶段或单元状态。
- 工具参数仅因可修正的结构校验失败时，允许模型进行一次有界修正，不产生无限重试或无依据数据。
- 部署后复用现有快照，只重试未成功单元，保留已经成功的结果。

## 方案比较

### 方案 A：仅把租约提高到 60 分钟

改动最小，但任务时长再次超过阈值时问题仍会重现，而且旧 attempt 的并发写入问题仍然存在。只适合作为部署期间的临时保护。

### 方案 B：仅在逐集协调器中续签

能解决当前逐集任务，但其他长时间统一执行仍会遇到同类问题。租约属于执行器职责，放在业务协调器会形成重复实现。

### 方案 C：执行器级心跳 + 写入 fencing + 有限格式修正

在 `AiExecutionWorker` 获取 claim 后启动统一租约守护，在退出时关闭；所有长任务自动受益。业务层在持久化失败状态前确认 attempt 仍有效，失效 attempt 直接退出。工具结构错误只允许一次模型修正。此方案是推荐并已确认的实现。

## 架构设计

### 执行租约守护

新增一个单一职责的租约守护组件，使用共享调度线程定期调用 `AiExecutionClaimService.heartbeat`。心跳间隔可配置，默认 60 秒，且必须小于 claim timeout。守护对象记录 claim 是否丢失并实现 `AutoCloseable`，确保任务结束时取消定时任务。

`AiExecutionWorker` 在 claim 成功后立即开启守护，并在 `finally` 中关闭。成功或失败落库前再次确认守护未丢失；若已丢失则抛出 `AiExecutionClaimLostException`，禁止旧 worker 完成任务。

### 旧 attempt 写入隔离

在分析阶段捕获异常并写 `FAILED` 前检查当前 execution id、version 和 attempt id 仍为数据库中的 `RUNNING/STARTED`。失效时抛出 `AiExecutionClaimLostException`，不调用 `failTask`，也不覆盖阶段状态。

逐集工具调用继续使用现有 `WorkflowAgentScopeGuard`。租约心跳正常时 scope 始终有效；真正失去租约时，旧执行只允许结束，不再把 claim-loss 转成普通业务失败。

### 工具参数有限修正

当工具调用因 `WORKFLOW_AGENT_TOOL_INVALID` 或参数 Schema 校验失败，并且尚未使用修正机会时，将结构化错误作为 tool message 返回模型，明确要求仅修复缺失或错误字段后重新调用同一工具。每次 Agent run 最多使用一次修正机会，继续失败则保留原始错误并终止。

`evidence` 必须来自当前剧集正文；系统不填充、不推断、不伪造证据。

## 状态流

1. Worker claim execution，创建 attempt。
2. 租约守护按固定间隔 heartbeat，持续延长 `claim_expires_at`。
3. 逐集协调器并发处理单元并逐个持久化成功结果。
4. 可修正的工具参数错误最多返回模型一次；第二次失败记为真实单元失败。
5. 正常完成后停止心跳并通过 claim token 原子完成 execution。
6. 进程退出或心跳失败时，调度器回收 execution；新 attempt 复用快照，只运行 `FAILED/STALE/PENDING`。
7. 旧 attempt 因 fencing 无法更新父任务或覆盖新单元。

## 配置

- 保留 `AI_EXECUTION_CLAIM_TIMEOUT`，生产临时设置为 `PT60M` 作为安全余量。
- 新增 `AI_EXECUTION_HEARTBEAT_INTERVAL`，默认 `PT1M`。
- `AI_WORKFLOW_FANOUT_CONCURRENCY=4` 在服务重启后生效。
- 应用启动时校验 heartbeat interval 小于 claim timeout。

## 测试与验收

- Worker 执行时间跨越心跳周期时，heartbeat 被调用且任务可正常完成。
- heartbeat 返回 false 时，旧 worker 不调用成功或失败终态写入。
- 旧 attempt 结束晚于新 attempt 时，父分析任务不会被改回 `FAILED`。
- 工具参数首次缺少 `evidence` 时触发一次修正；修正后成功。
- 连续两次无效参数后终止，且不存在第三次模型调用。
- 快照恢复只选择 `FAILED/STALE/PENDING`，已成功单元不重跑。
- 定向测试、后端完整测试和生产健康检查通过。
- 生产任务最终达到 58/58，execution、分析任务、阶段和快照均为成功状态。

## 部署与恢复

先部署代码和配置并重启服务。重启会中断当前 attempt，但现有快照会把运行中单元标记为 `STALE`。随后触发失败阶段重试，系统保留所有 `SUCCEEDED` 单元，只处理剩余单元。完成后核对单元计数、父状态和错误日志。
