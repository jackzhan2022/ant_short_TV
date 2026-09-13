# 验证记录

本记录只计本次实际执行；旧变更的勾选不视作本次验证。使用测试提供者和隔离数据库，未部署或连接实际业务库，未发起真实付费模型调用。

## 已完成

- 前端全量 Vitest：67 个文件、317 项通过。后续清理未使用代码、Space 属性和更新类型后，相关 3 文件 30 项再次通过。
- 最终删除测试夹具中的 candidateSaved 旧字段后，审核页面 12 项再次通过。
- `npm run lint`：Biome 和 TypeScript 通过。保留 requestErrorConfig.test.ts 的 3 条既有 document.cookie 警告。
- `npx antd lint ./src`：退出 0；当前 11 条警告为既有反馈 API、Modal.confirm 和 team/my 的 Tabs 属性。修改涉及的审核历史 Space 属性已修正。
- 前端生产构建通过；OpenAPI 再生成之后最终构建再次通过（Webpack 2.47 分钟，退出 0）。
- 当前后端 OpenApiContractTest 通过，导出 frontend/config/oneapi.json，并执行 `npm run openapi` 再生成客户端；旧 catalog/definition 客户端已删除，旧聚合、提取、候选端点扫描无匹配。
- 核心配置/退休接口定向回归 23 项通过：三个 classpath 模板、模型冻结 JSON、无旧接口付费调用、常驻调度、轻量查询边界。
- 正式概要/资产工具最终 45 项通过，覆盖四范围、CHARACTER 双提示词策略和 coverage/自动事件的事务原子性。最终分镜工具 9 项通过，覆盖派发后的人工新增保护（有/无稳定 episode_id）。
- ScopedAssetReextractionServiceTest 最终 10 项通过：四范围人工编辑保护、失败集重试与模型冻结、预检、成功/失败集来源变化、执行中变化、提交版本变化。新增 4 项先复现失败，再修复后通过。新 HTTP 预检、非法参数和权限拒绝用例也通过，拒绝后 operation/execution/reservation 无新增。
- 最终定向复核的 ProjectControllerTest 7 项、PointAccountingArchitectureTest 2 项、AutoStoryboardDispatchServiceTest 4 项通过。
- 后端全量过程中的审核仓储、轻量读取、QUICK/DEEP、控制器和服务回归已通过；后续定向配置/恢复回归中，模型快照 7 项、分析协调器 2 项、分析执行 9 项、分集端到端 6 项、图片控制器 15 项通过。
- 迁移/schema 52 项通过；增补后 7 项迁移回归通过。MySQL 8.4.6 空库、混合财务库和旧审核 Run/步骤取消演练通过。具体计数与历史跨 schema 限制见 data-cleanup-manifest.md。
- `openspec validate remove-legacy-ai-workflow-paths --strict` 通过。

## 后端全量与修复回归

后端全量使用独立 `.legacy-verification/target`；后续修复用 `.focused-verification/target`，避免覆盖正在执行的类文件。2026-09-12 16:31 完成全量：960 项，11 项失败、0 错误、1 项跳过（真实项目/提供者 smoke，默认不执行）。全量命令的原始退出码为 1，不能写成一次全绿；下列对应修复与后续用例均已通过补跑。全量日志为 `.legacy-verification/full-test.log`，定向输出见下表，均为本机验证产物，不是发布依赖。

| 全量失败类 | 修复与最终证据 |
| --- | --- |
| AiInvocationServiceTest（1） | 取消隐式旧 Agent 标签，7 项通过；focused-green.log |
| AiImageTaskControllerTest（2） | 补齐当前提示词夹具，15 项通过；focused-green.log |
| PointAccountingArchitectureTest（1） | V114 一次性迁移精确例外，2 项通过；final-focused.log |
| ProjectControllerTest（1） | 轻量工作台、按需正文/版本读取，7 项通过；final-focused.log |
| ScriptWorkflowControllerTest（3） | 迁到四 Agent 正式成果和当前提示词/读取契约；扩展后 24 项分批通过：final-focused.log 中 22 项、final-repairs.log 中分析 1 项、auto-green.log 中自动分镜 1 项 |
| VideoDecompositionExecutionServiceTest（1） | 当前模板日志契约，9 项通过；final-repairs.log |
| WorkflowAgentControllerTest（1） | 当前 20 个工具目录，3 项通过；final-repairs.log |
| ScreenplayToolCatalogTest（1） | 移除 6 个退役审核工具，1 项通过；guard-green.log |

最终 guard-green.log 为退出 0、25 项全部通过：Markdown 审核端到端 2 项、当前保存负载限制 5 项、作用域/旧阶段拒绝 17 项、工具目录 1 项。旧阶段空工具请求先由新增测试复现可绕过，再移除旧白名单并通过。

自动分镜端到端经过真实共享执行与积分预占：资金不足恢复、提交后崩溃恢复、提供者首次失败后由 worker 重试、正式分镜保存、资产 coverage 整行保持不变及人工新增关联保护。仅在提供者边界使用受控响应，未用 SQL 伪造执行成功。

临时 MySQL 8.4.6 实例（127.0.0.1:33917）已通过 mysqladmin 正常关闭；仅保留隔离测试数据目录和演练报告，未清理实际业务数据库。

## 运行代码扫描边界

退休标识只允许出现在历史迁移、退役迁移选择/验证、明确断言退休接口/阶段不存在的测试、保留财务关联的历史 Run 展示夹具、AlwaysOnAiSchedulingTest 的“旧属性 false 也不能关闭 bean”测试及标记为历史的文档中。合法模型状态、支付/存储/mock 配置、调用账本 scene 标识不属于新旧流程切换开关。

2026-09-12 最终扫描覆盖 backend/src/main/java/com、frontend/src、frontend/config、application.yml、env.example 以及执行/视频/自动分镜调度器。旧聚合接口、提取/候选 API、旧配置类、pipelineVersion/LEGACY_V1、candidateSaved 在当前运行代码中无匹配；测试中的命中仅为退休接口负向断言和迁移夹具。ScriptEpisodeReconciler/响应对象的 summary 为内存字段，当前读取仍来自正式仓储，未发现镜像列 SQL 或双写。

账本架构扫描仅对 V114 的一次性释放给出精确路径例外：Flyway 执行时 Spring 账本服务尚未初始化，迁移直接完成原子释放与当前 point_ledger 审计；其资金守恒、幂等和保留项由迁移测试独立验证。日常运行代码仍只通过统一账本服务修改账户。

追加扫描覆盖审核工具名称、STRUCTURED_JSON 和 DEEP_SEMANTIC：当前运行代码无匹配。ScopeGuard 仅接受 MARKDOWN_QUICK、MARKDOWN_DEEP_CHILD 和 MARKDOWN_DEEP_AGGREGATION，旧工具/旧阶段负载白名单已删除。所有实现任务已完成，实际部署仍按 deployment-checklist.md 执行。

## master 合并验证（2026-09-12）

在独立工作区将本变更与 master c2995c1 合并，保留任务中心及线上审计修复。审核页面冲突按仅 Markdown 路径解决，同时保留报告问题计数。

- 后端 ProductionTaskControllerTest、SchemaMigrationTest、WorkflowAgentScopeGuardTest 共 64 项通过，覆盖迁移至 V115 后的任务中心接口与作用域限制。
- 图片重新生成及视频技术重试的合并回归另有 3 项通过，验证任务中心入口、幂等和冻结执行快照。
- 前端审核、审核历史、分镜和任务中心共 7 个测试文件、59 项通过。
- Biome 检查通过（3 条既有警告）；antd 检查退出 0（11 条既有警告）。独立工作区使用 node_modules 目录链接，默认类型检查触发 TS2883 路径可移植性诊断；以 `npx tsc --noEmit --preserveSymlinks` 复核通过，未修改业务代码或类型检查配置。
- OpenSpec 严格验证及暂存差异空白检查通过。未部署或清理实际业务数据库。
