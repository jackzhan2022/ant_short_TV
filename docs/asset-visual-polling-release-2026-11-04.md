# 分镜页资产状态批量轮询发布记录

2026-11-04 09:56（北京时间）发布至 https://antv.aixmax.cn/。

## 版本与范围

- 应用提交：`de4fdaefcc5d96bcd2a44fc34371f674ae7bf3b6`，已推送远程 `master`。
- 发布目录：`/opt/antv/releases/20261104095508-de4fdaef-asset-visual-polling`。
- 上一版本：`/opt/antv/releases/20261103111752-c6ee43ca-compact-ai-model-dialog`，保留供回退。
- 后端与线上旧 JAR 逐文件比较，只新增 `AssetVisualStatusController`、`AssetVisualStatusService` 和其 `Status` 记录类；没有删除或修改已有类、依赖或资源。
- 前端从独立源码快照构建；3,329 个源码文件与提交快照逐项一致。未包含工作区其他未提交修改。
- 无数据库迁移，数据库版本仍为 172。共享环境、工作流技能、模型与计价配置摘要、迁移历史均与发布前一致。

## 验证结果与限制

- 前端全量 165 个测试文件、1,357 项测试通过；Biome、TypeScript、antd 检查及生产构建通过，保留工具原有警告。
- 后端轮询相关定向 9 项测试通过，覆盖批量查询、图片发布状态、权限与租户隔离、路由和分页形态引用。可执行 Spring Boot JAR 打包通过。
- 后端广泛回归在完成 123 个测试类、674 项测试后停止，发现 5 个测试类存在 5 项断言失败和 1 项错误。因此不宣称后端全量测试通过。随后在改动前提交 `2ab0055daafd906db22fbb74f55c1865b787c1b6` 的独立源码副本上重跑这 5 个类，26 项测试中复现完全相同的 6 项失败/错误。
- 历史失败涉及 `DramaPersistenceIntegrationTest`、`WechatComponentIntegrationTest` 的旧操作权限断言，`AiExecutionControllerTest` 的既有权限断言，`AiExecutionHandlerArchitectureTest` 的既有依赖规则，以及 `AlwaysOnAiSchedulingTest` 的不完整测试容器依赖。本次没有修改这些业务实现或测试。
- 生产 MySQL 上执行一次只读批量 SQL，查询 100 个形态、返回 100 行，并保存执行计划。该检查不代表并发容量压测。
- 上传包 SHA-256 一致，161 个前端文件逐项校验一致；保留 535 个旧静态文件以兼容已打开页面。
- 发布后服务 `active`、健康 `UP`、`MainPID=2412536`、`NRestarts=0`。公网首页及 7 个关键静态资源均 HTTP 200、SHA-256 一致；未登录的当前用户、租户管理及新状态接口均 HTTP 401。
- 公网验证不包含登录后的真实批量生图操作，没有为本次检查提交计费生成任务。

## 备份与回退

备份目录为 `/opt/antv/backups/20261104-asset-visual-polling`。数据库压缩备份为 177,411,613 字节，已验证解压完整性及结束标记；共享环境、工作流技能和服务定义一并备份。

发布先启动新后端并等待健康，再发布新前端。部署脚本具备失败时恢复上一版本并验证健康的回退逻辑；本次发布成功，未触发回退。

源码核对、构建与测试日志、基线失败对照、只读 SQL 验证、JAR 文件比较、备份回执、发布回执和公网验证结果保存于本机 `.release/asset-visual-polling-20261104/`。
