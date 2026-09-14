## Why

系统尚未对外上线，但剧本分析、审核、资产提取及管理面仍保留新旧实现与切流开关，造成同一操作具有不同保存、调度和配置语义。用户已确认历史业务数据可以清理，现在应将已实现的新链路收敛为唯一受支持路径，消除迁移兼容负担。

## What Changes

- **BREAKING**：四阶段分析、资产识别和分镜固定使用 Workflow Agent；QUICK/DEEP 固定使用最新 Markdown 审核链路，删除旧直接调用、结构化问题审核及其专用配置、入口和测试。
- **BREAKING**：固定使用逐集上下文、概要/识别独立推进及资产完成后自动分镜；移除 LEGACY_V1 执行分支和新旧切流、工作流启用、调度启停布尔开关。保留超时、并发、轮询间隔等运行参数和任务取消能力。
- **BREAKING**：移除旧元素提取/候选审核 API、旧工作台聚合读取 API、旧 Agent/Skill 管理模块。新管理模块统一显示为 Agent 管理、Skill 管理。
- **BREAKING**：清理受影响的历史业务数据、旧链路专用表/字段、概要双写及兼容读取。保留账号、团队、权限基础、模型/供应商配置、新 Agent/Skill 配置及无关业务数据。
- 以新增清理迁移和明确的数据依赖清单完成收敛，保留既有 Flyway 迁移历史；从空库和现有开发库均可得到同一当前结构。
- Agent、Skill 或模型缺失时返回可诊断错误，不自动回到旧执行器。共享执行、积分结算、租约恢复、鉴权与正式保存约束继续生效。

## Capabilities

### New Capabilities
- `single-path-ai-workflows`: 唯一新运行链、无切流/调度启停开关、旧 API 退役、开发期数据清理及部署契约。

### Modified Capabilities
- `script-analysis-pipeline`: 固定 Workflow Agent 与独立逐集分支，删除旧调度模式。
- `script-review-workflow-agent`: QUICK/DEEP 强制使用新 Agent，无旧直连回退。
- `script-element-workflow-boundaries`: 取消旧 JSON/候选提取与兼容审核，正式资产写入成为唯一 AI 提取路径。
- `short-drama-episode-summary-agent`: 删除旧概要镜像字段的双写和读取。
- `model-management-navigation`: 唯一 Workflow Agent/文件 Skill 管理面及独立权限。

## Impact

涉及 backend 的 script、review、workflowagent、旧 AI definition/catalog、执行与视频调度配置、数据库迁移，以及 frontend 的制作工作台、审核、模型管理、手写服务、路由和权限测试。自动生成的服务通过既有 OpenAPI 命令再生成，不手改。

与 `optimize-episode-analysis-context-pipeline`、`split-production-workspace-apis`、`add-new-agent-skill-modules`、`simplify-script-review-markdown-report`、`improve-script-review-quality` 等未归档变更交叉：继承其新功能，明确替代保留旧接口、开关回滚及历史兼容的要求；未完成的新功能验证仍需完成。本提案不执行数据库清理、部署或付费模型调用。
