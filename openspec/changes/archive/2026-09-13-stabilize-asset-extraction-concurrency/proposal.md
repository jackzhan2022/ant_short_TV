## Why

项目 33 的资产重提取任务因道具目录超过每类 200 条的硬上限而失败。按集执行仍读取全剧本目录；重复提交、任务恢复与多入口执行还需要一致的并发协调，防止重复付费调用、重复写入和错误清理。

## What Changes

- 复用现有统一执行系统，为同一租户、项目、剧本的资产提取增加原子提交防重与持久化执行互斥，覆盖剧本页识别和资产页重提取。
- 相同请求复用已有活动任务；不同范围或提示词策略的冲突请求明确返回冲突任务，不能静默改变用户意图。
- 复用正式写入端已有身份锁、名称规范化和别名匹配，补齐资产、变体、绑定及重试幂等验证和失效执行阻断。
- 将全量资产及变体预加载改为本集候选摘要、分页检索与按 key 读取详情；全局匹配仍由服务端兜底。
- 限定旧资产收口范围、源版本和执行所有权，失败、取消或过期任务不得执行清理。
- 增加并发、超大目录、两种后台身份以及故障恢复验收。本次不新增消息队列。

## Capabilities

### New Capabilities

- `asset-extraction-coordination`: 跨入口提交防重、持久化互斥、幂等写入及安全收口。
- `asset-catalog-retrieval`: 有界本集候选目录、全局分页检索和按 key 加载资产形态详情。

### Modified Capabilities

- `scoped-asset-reextraction`: 批量生成复用已有等价任务，冲突时展示任务信息，并保持用户确认与提示词策略语义。
- `short-drama-asset-recognition-agent`: 在读取当前剧集后允许按需检索资产和读取详情，再调用正式保存工具。

## Impact

- 后端：ScriptAiOperationService、ScopedAssetReextractionService、剧本分析资产阶段、EpisodeAssetPersistenceService、ScreenplayToolDataService、WorkflowAgentScopeGuard、Agent 工具注册及 Skill 指引。
- 前端：资产设定页提交响应、冲突提示与既有任务进度恢复；不改变 ALL / CHARACTER / SCENE / PROP 或提示词策略枚举。
- 数据库：执行协调记录及必要的幂等索引迁移，复用 script_asset_identity_lock；先审计历史重复记录，禁止静默删除或合并业务数据。
- 与 remove-legacy-ai-workflow-paths、optimize-episode-analysis-context-pipeline、add-production-task-center 的在途工作协调接口边界，不依赖其完成。
- 不增加外部基础设施，不在提案阶段修改线上资产或发起付费生成。
