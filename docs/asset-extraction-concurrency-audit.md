# 资产提取并发审计

审计日期：2026-09-12。对应变更：stabilize-asset-extraction-concurrency。

## 现有保障与缺口

| 边界 | 现有实现 | 需要补齐 |
| --- | --- | --- |
| 资产页提交 | ScriptAiOperationService 的事务按 tenant/operationType/client key 查询已有任务 | 不同 key 的等价请求防重；积分预占之前的跨任务 admission |
| 剧本页资产阶段 | EpisodeFanoutCoordinator 并发执行逐集单元 | 与 scoped operation 使用同一剧本协调记录 |
| 执行恢复 | Worker claim + lease keeper；异常进入 markFailed，有限次重试 | 等待协调不能消耗失败次数或被误结算为完成 |
| 正式身份创建 | 同规范名 identity lock；全局规范名/明确别名匹配，歧义拒绝 | 统一 key 更新和无 key 路径锁顺序；数据库 active identity 唯一兜底 |
| 资产索引 | V75 的 idx_*_asset_script_name 是普通 index | 不能把普通索引当作唯一性保障 |
| 形态和绑定 | V63 的 primary_marker、preferred_marker、active_binding_marker 有唯一约束 | primary 唯一不等于所有非主形态语义唯一；保留软删除语义 |
| 正式保存 | @Transactional + episode 行锁/内容指纹检查 | 在同一事务内核对 operation ownership 和 attempt |
| 提交证据 | 在途改动 EpisodeFanoutCommitEvidence 支持 analysis fanout unit | scoped snapshot/unit 未接入；需要恢复 RUNNING 且已提交单元 |
| scoped 快照 | loadOrCreate 内部使用 FOR UPDATE，方法无外部事务边界 | 查询锁不能跨自动提交语句起效；拆为代理服务或显式短事务 |
| scoped 收口 | 根据单元状态清理未绑定 AI 资产，无独立事务 | 提交证据、源集合及 owner fencing；事务清理后才释放 |
| analysis 收口 | AssetRecognitionFinalizer 有事务和 coverage 检查 | 校验共享执行拥有权及源版本，防止跨入口竞争 |
| Agent 授权 | 相同 asset Agent code 全部进入 SCOPED_ASSET_REEXTRACTION 查询 | 按可信 execution.business_type 分支，保留 SCRIPT_ANALYSIS_TASK |
| 上下文 | scoped 将 operation.id 填入临时 analysis task.id | contexts.prepare 会按同数值查 analysis 历史，必须拆分身份语义 |
| 后续事件 | recordAutoStoryboard 按 taskId 查询分析任务 | operation ID 碰撞可能误触发，应同时验证 business_type/execution 归属 |

## 与在途改动的边界

当前工作区含 remove-legacy-ai-workflow-paths、add-production-task-center 等未提交改动。AssetRecognitionAgentAdapter 增加 executeClaimedChild/trustedToolState，正式保存增加 EpisodeFanoutCommitEvidence；本变更需叠加到这些接口，不能恢复旧文件覆盖它们。共享任务等待状态、计费和失败恢复应通过独立测试验证。

## 只读预检

使用 scripts/sql/asset-extraction-duplicate-preflight.sql，在授权数据库执行只读查询。报告相同业务域规范名重复、空规范名以及重复形态；重复形态仅是人工核查候选，不能据此自动合并。所有结果不修改业务数据。索引迁移前必须确认非空 active identity 冲突已处理。

本次已在生产数据库执行只读预检：规范名重复查询及形态同名候选查询均无记录；三类缺失规范名计数均为 0。此结果仅代表审计时刻，正式迁移前需要重新执行。基线 WorkflowAgentScopeGuardTest、ScopedAssetReextractionServiceTest、AssetRecognitionAgentAdapterTest、EpisodeAssetPersistenceServiceTest 的 Maven 命令退出码为 0。

## 本次实施阻塞

新增守卫测试已复现 SCRIPT_ANALYSIS_TASK 被误判为重提取操作的问题，随后添加 business_type 分流以及重提取独立上下文准备。验证期间，其他在途变更导致 ReviewProjectProgressiveReadIntegrationTest:213 引用不存在的 outstandingIssueCount()，Maven testCompile 失败。另一次直接运行 surefire 时共享 target 中已无匹配测试类，且仍有其他 Java 进程运行；因此不能将缓存测试或共享构建产物当成本次修改的验证结果。

任务 2.4 的代码为待验证状态，不勾选完成。未完成的协调服务和迁移草稿已撤回，避免只落库不接入执行的半成品被其他发布携带。继续时需先取得稳定构建快照或协调其他在途构建，然后完成身份回归与剩余任务。
