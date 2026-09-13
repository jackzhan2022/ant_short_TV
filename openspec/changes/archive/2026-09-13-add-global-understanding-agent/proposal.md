## Why

当前“剧情全局理解”仍由固定的剧本分析执行服务直接调用模型并保存阶段 JSON，无法作为“Agent（新）”中的独立工作流运行，也没有一份按剧本关联、可被后续页面和其他 Agent 直接使用的正式全局理解数据。需要先把这一环节迁移为可独立读取当前剧本、按统一 Skill 框架分析并通过专用工具落库的 Agent，作为其余三个分析 Agent 的实现基线。

## What Changes

- 新增并初始化启用的 `short-drama-global-understanding` 工作流 Agent，关联短剧分析基础 Skill、剧情全局理解框架 Skill，以及读取当前剧本和保存全局理解两个工具。
- 新增 `read_current_script` 只读工具，使 Agent 在每次运行时根据服务端可信 `scriptId` 获取当前剧本正文，而不依赖调用方注入正文或历史上下文。
- 新增 `save_global_understanding` 写工具；只有该工具成功写入正式数据后，Agent Run 和对应分析阶段才能完成。
- 新增按 `script_id` 唯一关联的当前全局理解存储，以固定归属元数据加 `content_json` 保存可扩展内容；重新运行覆盖当前记录，不建立业务版本回溯，也不关联 `script_version_id`。
- 扩展工作流 Agent 可信作用域、必需工具和终止型写工具语义，禁止模型提供或修改租户、项目、剧本、任务、阶段和运行标识。
- 将现有剧本分析流水线的“剧情全局理解”阶段委派给新 Agent；其余三个阶段暂时保留现有实现。
- 暴露可恢复的 Agent 运行与阶段进度，包括读取剧本、分析、保存、完成和失败状态。

## Capabilities

### New Capabilities

- `script-global-understanding-agent`: 独立剧情全局理解 Agent 的配置、Skill 约束、当前剧本读取、正式 JSON 文档落库、幂等覆盖和运行完成条件。

### Modified Capabilities

- `script-analysis-pipeline`: 将剧情全局理解阶段改为调用工作流 Agent，并以专用保存工具成功作为阶段完成条件，同时保持后续三个阶段的现有顺序与依赖。
- `script-analysis-progress`: 显示剧情全局理解 Agent 的真实读取、分析、保存和失败进度，且仅在正式数据持久化后显示该阶段完成。

## Impact

- 后端工作流 Agent 运行器、可信作用域、工具注册表、工具 Schema 校验、运行/步骤审计和剧本分析阶段适配器。
- 新增剧情全局理解正式数据表及 Flyway 迁移；现有 `script_analysis_result` 继续保留诊断与审计用途。
- 新增两个文件型 Skill 和一个内置工作流 Agent 的可重复初始化逻辑。
- 剧本分析工作区接口及进度映射会增加 Agent Run 和正式全局理解状态，但本变更不设计最终页面展示布局。
- 不修改剧集拆分、剧集概要、角色场景道具识别的执行与落库方式；它们将在后续独立变更中迁移。
