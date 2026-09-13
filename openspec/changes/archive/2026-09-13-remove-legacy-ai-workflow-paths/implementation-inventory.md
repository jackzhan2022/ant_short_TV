# 唯一链路实施清单

核对基线：94d362f。本文件记录当前运行代码的退役目标；完成以 tasks.md 与验证记录为准。

| 范围 | 当前入口/依赖 | 唯一目标 |
| --- | --- | --- |
| 四阶段分析 | ScriptAnalysisExecutionService 的 enabled 分支、splitEpisodes/summarizeEpisodes/invoke | 四个正式 Agent 与已持久化 fanout |
| 分析任务模式 | ScriptAnalysisTaskService、ScriptAnalysisTaskEntity、ScriptWorkflowResponses、前端 service 的 pipelineVersion | 删除旧模式选择和对应响应 |
| 自动分镜 | EpisodeAssetPersistenceService 的 autoStoryboardEnabled/pipeline_version；AutoStoryboardDispatchService.enabled | 正式识别 coverage 后记录事件并正常派发 |
| 审核 | ReviewWorkbenchService、ReviewQuickAgentAdapter、ReviewDeepAgentCoordinator、ReviewWorkflowFeatureFlags | Markdown 单元和正式报告 |
| 工作台聚合 | ScriptWorkflowController 的 script-workspace/asset-settings-workspace；ScriptWorkflowService.workspace 与写操作返回 | 轻量读 + mutation 最小结果 |
| 旧提取 | scripts/ai-extract-elements、asset-candidates 列表/详情/decisions、旧元素确认 | scoped reextraction + 正式人工编辑 |
| 旧配置 | BuiltInAgentCatalogController、EditableAiDefinitionController、BuiltInPromptTemplateRenderer、ScriptAnalysisConfigSnapshotService | Workflow 配置或仍适用业务的单一模板源 |
| 后台调度 | AiExecutionDispatcher.enabled、AiVideoTaskScheduler/VideoDecompositionTaskScheduler 的 ConditionalOnProperty | 无启停布尔开关的正常调度 |
| 初始化 | 各分析、分镜、审核 Bootstrap enabled | 幂等初始化当前配置 |

## 必须保留的调用者与边界

- BuiltInPromptTemplateRenderer 不只服务旧分析，还解析 video.understanding.analysis、video.script.draft。删除旧配置域之前必须迁移这些正常业务调用者。
- ScriptWorkflowController 的 save/apply/asset/storyboard 写接口现返回 Void；前端写成功后只重读轻量数据，读失败的重试不会再次提交写操作。
- asset_visual_variant、asset_visual_variant_episode、script_episode_asset_analysis 是正式资产数据，不能因旧候选表相邻而删除。
- review_fanout_snapshot、review_fanout_unit、Markdown 单元数据仍用于新版审核，不能整体删除审核 fanout。
- ai_model_parameter_profile 属于模型运行配置，不随 ai_agent_definition/ai_skill_definition 删除。

## 已落实的调用迁移

| 调用者 | 当前唯一契约 |
| --- | --- |
| script.tsx 保存/应用版本 | Void + script-page-workspace、script-content、按集 detail |
| settings.tsx 修改/删除资产 | Void + asset-settings-summary；visual 单独 detail |
| storyboard.tsx 创建/修改/移动/确认 | Void + storyboard-workspace 分页；删除成功移除当前列表项 |
| ScriptEpisodeService、ScreenplayToolDataService | script_episode_summary.content_json；缺失为空，无镜像回退 |
| ScreenplayToolDataService.saveEpisodeSummary、人工 updateEpisodeSummary | 仅正式概要仓储写入，保留生成 Run 与内容指纹 |
| ScriptWorkflowService 改写 | classpath prompts/script-rewrite.md |
| VideoDecomposition 分析与草稿 | classpath prompts/video-understanding.md、video-script-draft.md |
| 分析执行与取消后重启 | 首次可用 modelId 冻结；执行与恢复严格读取快照 |

旧业务 scene 枚举只为账本和历史调用保留，不再注册旧提取 handler。模型启用状态、付款/存储/mock 配置属于有效产品配置，不是新旧流程切换或调度开关。

## 活跃规格替代关系

- simplify-script-review-markdown-report：采用 Markdown 成功条件，替代其历史结构化只读分支。
- improve-script-review-quality：保留当前维度、可信范围和缓存观测；结构化候选、语义裁决与门禁已被 Markdown 替代。
- optimize-episode-analysis-context-pipeline：继承新逐集语义，旧任务及开关回滚条款退役；未完成端到端验证仍需补齐。
- split-production-workspace-apis：采用轻量 API，取消旧聚合兼容；性能验证不能用文档任务已勾选替代。
- add-new-agent-skill-modules：保留新配置及权限，取消旧管理模块和七页签兼容条款。
