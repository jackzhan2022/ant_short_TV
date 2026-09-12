# 实施验证与部署说明

实施分支：codex/add-production-task-center。
实施目录：D:/信计软件项目/ant_short_TV/.worktrees/production-task-center。
原工作区存在 remove-legacy-ai-workflow-paths 相关未提交改动，本次未覆盖或修复这些改动。任务中心单独提交并合入本地 master；主目录继续保留原开发分支及未提交改动。部署和归档未执行。

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

## 部署及回滚（仍待部署环境验收）

1. 使用独立依赖安装运行 CI；先发布后端聚合端点，再发布前端 /tasks 入口。不需要额外数据库迁移。
2. 在目标 MySQL 版本及代表性数据量上，对列表、summary、详情与子项的实际 SQL 执行 EXPLAIN ANALYZE，检查 tenant 条件、索引、批次聚合行数、排序开销与端到端延迟。重点覆盖大量历史任务、分镜警告查询、mine/team 以及末页查询。按实际计划决定是否增加索引。
3. 验收 OWNER、有效系统 ADMIN、普通成员、成员撤销、项目权限撤销，以及其他成员只读；确认原生产入口仍可提交、完成与查看结果。
4. 回滚先撤下新前端入口，再回滚后端构建。由于无结构迁移，不删除任务、结果、执行、积分预留或计费记录。复验原生产入口与后台任务继续运行。

未执行目标环境部署、MySQL 实库压测或部署回滚演练。因此 tasks.md 的 6.3 保持未完成；其余实施与本地验收已完成。语音/合成同步能力缺口及受限控制边界详见 coverage.md，不宣称已提供尚不存在的异步能力。
