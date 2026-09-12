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

本次已在生产数据库执行只读预检：规范名重复查询及形态同名候选查询均无记录；三类缺失规范名计数均为 0。此结果仅代表审计时刻，正式迁移前需要重新执行。正式持久化行为通过实际存在的 ScreenplayToolDataServiceTest 和 AssetExtractionCoordinationTest 验证；不能将命令中未匹配到的测试类计为通过。

## 隔离实施与验证

按用户要求在 `.worktrees/asset-extraction-concurrency`、分支 `codex/stabilize-asset-extraction-concurrency` 隔离实施，保留原工作区脏改动快照。原编译阻塞 `outstandingIssueCount()` 已在取得的在途快照中修正，隔离工作区重新编译和测试，不依赖共享 target 的缓存产物。

已接入剧本级协调记录、条件 admission、资源等待和旧 attempt fencing。剧本页在整个分析开始时取得拥有权，以保证等待发生在模型调用前；拥有权保留到统一执行终态，随后在下一次申请中锁定并回收。MySQL REPEATABLE READ 下通过当前锁定读取返回并发刚创建的 execution，避免旧事务快照漏读。

正式保存事务检查拥有权、有效租约、当前版本/源正文并写入提交证据。scoped 执行恢复复用已提交单元，冻结 Agent/Skill 计划，拒绝被其他任务覆盖的旧提交证据。收口事务校验完整证据，保护手动与范围外数据。执行结果与结算重新核对当前 claim，费用按整个 execution 的持久化调用归集。

本地独立 MySQL 8.4.9（127.0.0.1:13316）完成迁移与并发验收，没有写线上数据。专用协调测试覆盖 13 个用例，包括双请求仅一个 operation/reservation、跨入口、软删除唯一性、取消/租约接管、保存后崩溃、源版本变更和幂等收口。发布、回滚、测试命令及项目 33 的付费验收步骤见 `docs/asset-extraction-concurrency-rollout.md`。最终检查结果以该文档和任务清单为准。
