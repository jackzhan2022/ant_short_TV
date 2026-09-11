# 交接：剧本审核列表优先渲染

## 已交付

- 新增 `GET /api/script-review/projects/summaries`：只返回列表首屏所需的项目字段。
- 新增 `GET /api/script-review/projects/metrics`：批量返回版本数、最新轮次、审核状态、待处理数和操作文案。
- 列表与“引用审核剧本”选择器都改为摘要先渲染、指标后补齐；指标失败不会清空已加载项目，并提供仅重试指标的入口。
- 通过批量权限筛选和聚合投影避免加载剧本正文、报告正文、问题证据等长文本字段。
- 新增 `V108__optimize_review_library_progressive_reads.sql`，为项目列表和最新任务选择增加复合索引。
- 保留旧 `GET /api/script-review/projects` 接口，支持旧客户端回退。

## 已验证

- 后端：摘要/指标接口契约、指标状态、Flyway 索引迁移定向测试。
- 前端：列表摘要先到、指标失败重试、选择器异步版本数用例；TypeScript 类型检查。
- OpenSpec 变更校验：`openspec validate prioritize-script-review-list-rendering --strict`。

## 后续验证清单

- 补齐租户管理员、项目成员、移除成员和跨租户的授权矩阵测试。
- 使用 SQL 捕获补充固定查询次数及禁选长文本字段的断言。
- 在生产样本 MySQL 上执行 `EXPLAIN ANALYZE`，记录旧接口、摘要接口和指标接口的 p50/p95、SQL 耗时、响应大小。
- 观察旧 `/projects` 接口流量后，再决定下线计划。

## 回退

前端可以恢复调用旧 `GET /api/script-review/projects`；新增接口和索引为加法变更，可保留。
