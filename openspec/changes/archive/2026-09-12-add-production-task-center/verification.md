# 实施验证与部署说明

实施分支：codex/add-production-task-center。
实施目录：D:/信计软件项目/ant_short_TV/.worktrees/production-task-center。
原工作区存在 remove-legacy-ai-workflow-paths 相关未提交改动，本次未覆盖或修复这些改动。任务中心提交 6652857 已合入并推送 master，部署到内测主机；主目录继续保留原开发分支及未提交改动。变更于 2026-09-12 归档，增量规格已同步到主规格。

## 本地验证（2026-09-12）

后端使用 JDK 17、Maven、真实 H2 数据库及既有 107 项 Flyway 迁移（版本至 V112）。下列最终相关测试共 32 项通过，无失败或跳过：

| 测试 | 数量 | 证据 |
|---|---:|---|
| ProductionTaskControllerTest | 9 | 当前成员/系统管理员/所有权转让、跨团队和创建人越权、权限撤销、批次分页及历史记录、共享分镜执行归属、真实分镜警告、混合来源、未知状态、JDBC 数值布尔值、分页查询数 |
| AiImageTaskControllerTest.regeneratesIntoNewDomainTaskAndNextExecutionVersion | 1 | 真实创建/完成/再生成，中心重复键返回相同新任务；旧结果与预留记录保留；变体再生成被拒 |
| VideoDecompositionControllerTest.technicalRetryKeepsTheFrozenExecutionAndPricingSnapshot | 2 | 原领域入口与中心入口均重试原执行、保留计价快照，不改变成功兄弟项 |
| AiExecutionCoreServiceTest | 11 | 复用执行层的状态/版本/计费相关回归 |
| ReviewAccessGuardTest | 6 | 审核稿读取/编辑授权回归 |
| StoryboardBatchServiceTest | 2 | 分镜批次状态和成功警告语义 |
| VideoDecompositionRetryPolicyTest | 1 | 拆剧领域重试准入 |

实际运行的 Maven 选择器分批覆盖以上集合，最近改动后重跑 ProductionTaskControllerTest 与图片领域再生成测试。测试报告位于 backend/target/surefire-reports。

前端 npm test -- src/pages/tasks/index.test.tsx：7 项通过。覆盖我的/团队视图、越权直接 URL、仅加载选中详情、URL 分页恢复、跨团队迟到响应隔离、隐藏页暂停请求、终态/卸载停止轮询、网络失败后幂等键复用。

前端检查：

- npx biome check src/pages/tasks：通过。
- npm run lint -- -- --preserveSymlinks：Biome 与完整 TypeScript 检查通过，仅既有 6 个 Biome 警告。
- npx antd lint ./src：退出码 0，仅既有 14 个警告，新页面无报告项。
- 编写组件前已查询 Table/Drawer/Select/Button/Alert/Input/Progress/Tabs 的 antd API。未编辑生成的 services/ant-design-pro。

工作区 node_modules 通过 junction 复用原目录依赖。原样 npm run lint 在 config/config.ts 触发 TS2883（推断类型路径落到原目录）；上面的 preserveSymlinks 参数只调整此环境的模块路径解析，不关闭类型检查、不修改仓库配置。独立安装依赖的 CI 应继续运行标准 npm run lint。

OpenSpec：在实施工作区根目录运行 openspec validate add-production-task-center --strict，通过。git diff --check 通过。

## 本地性能和历史兼容证据

列表完全在数据库筛选、排序、计数与 LIMIT/OFFSET；每个来源下推 tenant，每类批次只聚合自身子来源。H2 EXPLAIN 测试验证执行计划包含租户条件、索引访问与 20 行上限。既有表已有 tenant 开头的索引，尚无证据支持额外索引迁移。

历史测试包含 105 个没有 execution 的分集；pageSize=999 被限制为 100，总数仍为 105。30 个批次的查询统计测试对比 1 行/20 行页，确认数据库调用数不随每行增长。混合图片、视频、分析、审核验证活动优先、过滤、分页、总数与 summary；真实 shot_plan 警告和空警告数组分别验证。

本次没有新增表、迁移、重复状态机或数据回填。原业务端点和调度实现未修改，原入口与中心入口的领域回归共同验证兼容性。此证据不等于生产 MySQL 负载测试；本机未发现 MySQL/Docker 命令或本地 3306 监听。

## 部署环境验收（2026-09-12，已完成）

按 docs/antv-deployment-runbook.md，在 antv-prod 内测主机完成真实 MySQL 查询性能验证和应用版本回滚。当前版本为 `/opt/antv/releases/202609121450-6652857`，站点为 https://antv.aixmax.cn/ 。MySQL 为 8.0.46-0ubuntu0.24.04.3，Flyway 仍到 V112；没有新增迁移、索引、视图或数据回填。

### 发布构建

从干净的 6652857 提交建立独立发布工作区 `.worktrees/task-center-release`，独立执行 npm ci。标准 npm run lint 与 npx antd lint ./src 均退出 0，不再需要 preserveSymlinks；仍有既有 6/14 个警告。任务中心及部署手册要求的 billing/commercial/packages 测试合计 5 个文件、34 项通过。npm run build 和 mvn -q -DskipTests package 通过，JAR 已验证包含 BOOT-INF/classes。

发布前确认远端 master 为 6652857。上传包与本地 SHA-256 一致：

- 后端：`09b2503867fc736bf6de135ff7143a077c7f3a0f9893071242a060f7e42229f1`。
- 前端：`356d083c46b62f24c3f1db4bb042d55fad2ff3fbe1b9b7651743633d27bde1ca`。

### MySQL 性能

原始证据见 [mysql-baseline.json](evidence/mysql-baseline.json) 和 [mysql-scaled.json](evidence/mysql-scaled.json)。Java 导出工具直接引用应用 ProductionTaskSources，避免重新实现聚合模型。真实业务表只执行只读查询；独立的 `ptc_accept6652857_` 测试表使用 CREATE TABLE LIKE 复制结构及索引，全部填入合成数据，没有复制用户正文。

合成场景共 122,000 行，覆盖两个租户、各类来源、分镜警告、空批次、成功/失败历史和批次子项。目标租户共有 41,200 个顶层任务，末页 offset=41,180，页大小 20。对 9 类 SQL 执行 EXPLAIN ANALYZE 和 7 次串行计时；语句上限 5 秒。测试结束后确认测试表数量为 0。

以下为客户端单条 SQL 中位数，单位毫秒，包含 mysql 进程与数据库连接开销，不等于纯服务端时间或整页 HTTP 延迟：

| 查询 | 现有数据 | 合成大数据 | 大数据最大值 |
|---|---:|---:|---:|
| 我的列表 | 60.35 | 259.16 | 260.11 |
| 团队列表 | 60.51 | 708.26 | 742.03 |
| 末页/深分页 | 59.91 | 751.54 | 758.90 |
| 失败及时间筛选 | 59.90 | 349.69 | 351.32 |
| 总数 | 59.94 | 683.88 | 753.14 |
| 状态统计 | 60.52 | 702.04 | 707.78 |
| 详情 | 59.14 | 134.29 | 137.13 |
| 子项 | 58.74 | 108.78 | 109.85 |
| 分镜警告富化 | 56.27 | 87.08 | 90.90 |

现有数据的深分页使用 offset=100；合成场景动态计算真正末页。详情/子项基准使用 min(id) 子查询选取目标，应用实际请求使用具体 ID。现有数据 children 有一次 326.61 毫秒抖动，已保留原始样本，没有以中位数掩盖最大值。现有分镜批次为空，因此非空警告路径由合成数据及本地集成测试覆盖。

执行计划显示大多数来源使用 tenant 开头的索引，execution 关联使用主键；批次来源各聚合自己的子表。小批次表扫描约 1,000 行。团队列表会物化约 58,000 个派生行，再筛选出 41,200 个顶层任务并排序；主要成本是聚合与物化，不能把 LIMIT 20 理解为只读取 20 行。现有索引已被使用，未凭猜测增加索引。

结论：目标 MySQL 的 SQL 兼容性、有限规模串行性能和分页验收通过；不代表高并发或无限历史量容量验收。一次列表请求还包含 count 与富化，列表与 summary 合计在大数据场景可能超过 2 秒。更大历史量或高并发上线前应另行测量并评估查询裁剪/摘要投影，不把本次结果作为容量 SLA。

### 应用回滚与恢复

备份位于 `/opt/antv/backups/20260912-task-center-6652857-064758`，数据库压缩包 34,289,518 字节，Skill 压缩包 20,345 字节。已验证 gzip 完整性、mysqldump 完成标记和 Skill tar 可读取；摘要见 [backup.json](evidence/backup.json)。这是备份完整性检查，本次没有执行数据库恢复演练。

| 阶段 | 版本 | 重启至健康检查通过 |
|---|---|---:|
| 发布新版本 | 6652857 | 12.73 秒 |
| 回滚旧版本 | 94d362f | 12.62 秒 |
| 恢复新版本 | 6652857 | 12.56 秒 |

证据为 evidence/release-*.json、http-*.json 和 snapshot-*.json。测量包含轮询间隔，代表本次重启恢复时间，不宣称零停机。三次均先确认没有活动执行、运行中的分析/审核或自动拆剧任务，再原子切换 current 并重启。共享 env、workflow-skills 和 review-exports 保持原位置，旧版本保留。

正常注册专用验收账号并经原 API 创建团队、空项目；在该隔离团队写入一个明确标识的无 execution 历史终态拆剧样本及固定剧本结果，没有提交付费 AI 请求。三个版本阶段均正常登录、读取项目、批次、分集和剧本结果，结果正文逐字一致。新版本 mine/team/summary/detail/children 返回 200，计数一致、历史状态正确；越权访问团队 1 返回 403。回滚后中心 API 返回 404 且前端任务包不存在；恢复后中心 API 和 /tasks 恢复 200。新版本中心 5 类 HTTP 读取约 70–159 毫秒，样本仅包含一个批次，不能外推到大数据量。

16 张任务/执行/结果/积分相关表逐行计算哈希，再对有序摘要计算 SHA-256；连同分析任务关键状态和迁移摘要，共 18 组在 baseline/new/rollback/restored 四阶段完全一致。包含 150 条执行、150 条积分预留、357 条积分流水。既有分析任务 #4（团队 18）仍为 PENDING、无 execution，既有调度器每 8 秒更新它的时间，因此分析任务只比较 id/tenant/status/execution，未误报 updated_at 变化。没有恢复数据库、删除业务任务或重建积分。

最终 [acceptance-result.json](evidence/acceptance-result.json) 确认服务 active、当前版本 6652857、迁移 V112、活动执行 0、测试表 0。验收团队 25 和账号 23 已停用，其会话全部撤销；本机服务器临时目录内的测试密码和 cookie 文件已删除。项目 34、批次 21、分集 32 及固定结果留在停用验收团队，便于审计，未删除真实业务数据。

线上角色验收覆盖 OWNER 与跨团队拒绝；普通成员/系统 ADMIN/角色撤销/项目权限撤销/他人只读等完整矩阵由上面的本地集成测试覆盖。本次没有在线触发付费生产、运行中任务抢占/重试或浏览器交互回归，不把它们计为部署演练结果。语音/合成同步能力缺口及受限控制边界仍见 coverage.md。

tasks.md 的 6.3 已完成。验收工具位于 scripts/production-task-acceptance，使用说明见其 README；本次只新增验收工具与证据，没有修改已发布应用代码。
