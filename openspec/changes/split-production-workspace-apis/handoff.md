# 制作台读取链优化交接（2026-09-11）

## 当前交付

- 工作分支：`master`；此前已发布的线上版本为 `c536c33`，本轮后端读取链优化尚待本次 Git 推送后的独立发布。
- 剧本与分镜的分集导航改用轻量投影，不再从 `script_episode` 读取或在响应中返回每集 `content`；详情正文仍由现有按需详情读取承接。
- `current-analysis` 将阶段最新结果、Agent Run、fan-out 快照分别改为批量查询，避免这三类查询随阶段数线性增加。
- 资产视觉工作区在取得项目访问上下文后复用该上下文做权限判定，避免同一请求的第二次项目成员解析。

## 已验证边界

- `ScriptWorkflowReadBoundaryTest`：剧本页和分镜页不再调用会加载完整分集正文的 `currentEpisodes`。
- `ScriptAnalysisReadBoundaryTest`：分析状态不再以每阶段单独读取结果、Run 与 fan-out 快照。
- `ScopedPermissionGuardTest`：复用访问上下文的权限检查不再调用项目解析器。
- 历史验证、线上 `c536c33` 的发布证据以及认证性能采样缺口见 [verification.md](verification.md)。

## 尚未完成（不可在验收中勾选）

1. `8.2` 仅完成结果、Run、fan-out 快照的批量化；`ScriptWorkflowService` 中 `fanoutProgress` 仍逐快照读取 units、cache、timing，`splitProgress` 仍逐拆分阶段读取。后续应将这些明细按 snapshot/run ID 批量聚合，并把回归断言扩展为多阶段、多 fan-out/split 的固定查询上界。
2. `8.4` 尚未实现。需要为选定的制作台 GET 路由增加脱敏的请求级 HTTP/SQL 耗时与查询数观测；不要记录 Cookie、Authorization、正文或用户标识。
3. `7.2` 尚未完成。需在发布本轮提交后，以项目 33 的认证态采集 `script-page-workspace`、`asset-settings-summary`、`storyboard-workspace` 的耗时、响应体积和 SQL 查询数，并与旧版本/旧链路对照。浏览器桥接此前不可用，不能将未认证 401 或 HAR 推断当成认证性能结论。

## 后续建议顺序

1. 发布本次 `master` 后，先完成项目 33 的认证态前后对照，确认轻量分集投影确实降低响应体和读取时间。
2. 继续完成 `8.2` 的明细批量化，并新增覆盖 N 个阶段的查询上界测试。
3. 完成 `8.4` 的脱敏观测后，再基于真实 SQL 耗时判断是否需要处理数据库慢查询、索引、连接池或外部依赖。

设计依据见 [后端读取优化设计](../../../docs/superpowers/specs/2026-09-11-production-workspace-backend-read-optimization-design.md) 和 [实施计划](../../../docs/superpowers/plans/2026-09-11-production-workspace-backend-read-optimization.md)。
