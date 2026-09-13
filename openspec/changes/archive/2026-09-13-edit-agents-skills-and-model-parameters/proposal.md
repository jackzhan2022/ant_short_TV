## Why

剧本解析依赖的 Agent、Skill 和模型参数目前主要由代码固定，运营人员无法在管理页面修订提示词或调整输出上限。项目 19 已暴露出长剧本调用达到 `max_tokens=2048` 后返回内容无法通过 JSON 校验的问题，因此需要让可编辑配置真正进入 AI 调用链。

## What Changes

- 新增 Agent 管理编辑能力，支持修改名称、描述、提示词模板、输出结构约束和关联 Skill，并持久化版本。
- 新增 Skill 管理编辑能力，支持修改名称、分类、内容和启用状态，并持久化版本。
- 新增模型文本参数配置，包括温度、最大输出 token、top-p、JSON 输出模式、超时和重试策略。
- AI 调用按业务场景读取生效的 Agent/Skill/模型配置，并将其传入供应商请求。
- 增强 JSON 响应校验和失败诊断，区分非 JSON、截断和结构不完整等情况。
- 保留内置默认配置作为迁移和回滚兜底。

## Capabilities

### New Capabilities

- `editable-ai-agents-skills`: 管理员可编辑、版本化并启停 Agent 与 Skill，配置对后续 AI 调用生效。
- `configurable-ai-model-parameters`: 管理员可配置模型调用参数，系统校验范围并将参数应用到文本调用。

### Modified Capabilities

无。

## Impact

- 后端 AI 配置领域模型、数据库迁移、管理 API、权限校验和调用编排。
- 前端模型管理下的 Agent/Skill 两个 Tab 及模型参数编辑表单。
- OpenAI 兼容供应商请求组装、JSON 响应处理、调用日志和剧本解析任务。
- 需要新增迁移（当前线上版本为 V58），并兼容已有代码内置配置和已有项目配置。
