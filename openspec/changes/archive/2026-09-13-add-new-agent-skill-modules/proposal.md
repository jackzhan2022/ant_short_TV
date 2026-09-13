## Why

现有 Agent 管理和 Skill 管理主要服务于内置配置，无法安全地创建、编辑并立即运行由提示词、Skill 和工具共同组成的可配置工作流。需要在不影响既有调用链的前提下，新增一套独立模块，为后续逐步迁移业务 Agent 提供可管理、可测试、可审计的运行基础。

## What Changes

- 在“模型管理”现有五个页签之后新增独立的“Agent（新）”和“Skill（新）”页签，保留原“Agent 管理”和“Skill 管理”及其行为不变。
- 新增 Agent 的创建、编辑、复制、启停、受引用约束的删除和测试运行能力；配置保存后立即生效，暂不提供草稿、发布或版本回滚。
- Agent 使用纯文本系统提示词描述工作流，可显式绑定模型、Skill 和后端工具；工具选择会建立真实白名单关系，并可将可读的工具调用文本插入提示词。
- 新增同项目文件型 Skill 管理，以 `skills/<skill-code>/SKILL.md` 为存储单元，支持创建、编辑、复制、引用查询和受引用约束的删除；保存后立即影响所有引用该 Skill 的 Agent，暂不提供版本管理。
- 新增只读工具注册表和基于模型原生 Tool Calling 的 Agent 执行循环；运行时只暴露 Agent 明确绑定的工具，并注入受信任的租户、项目、剧集和用户上下文。
- Agent 测试运行支持使用尚未保存的表单配置，正式运行则始终加载当前已保存配置；每次运行记录 Agent 配置、Skill 内容摘要、工具集合及逐步调用日志，以便审计和排错。
- 首批提供剧本读取、上下文读取、格式校验和剧集版本保存等受控工具；`save_episode_script` 创建新剧集版本并自动设为当前版本。
- 新增独立权限控制新模块的查看和编辑操作，旧模块权限与 API 保持不变。

## Capabilities

### New Capabilities

- `workflow-agent-management`: 独立 Agent 的配置生命周期、模型/Skill/工具绑定、即时生效和测试运行。
- `file-backed-skill-management`: 同项目 `SKILL.md` 的安全创建、编辑、读取、复制、引用保护和即时加载。
- `agent-tool-runtime`: 只读工具目录、显式工具白名单、作用域上下文注入、Tool Calling 执行循环与运行审计。

### Modified Capabilities

- `model-management-navigation`: 在保留现有五个页签及权限行为的同时，追加“Agent（新）”和“Skill（新）”两个独立页签。

## Impact

- 前端模型管理页签、Agent（新）/Skill（新）列表与编辑器、工具选择器和测试运行面板。
- 后端新增独立的 Agent 配置、关联关系、工具注册与运行 API，不复用或改变既有 Agent/Skill API 的语义。
- 数据库新增 Agent（新）配置、Agent-Skill、Agent-Tool、运行记录及步骤日志相关表和权限迁移。
- 后端部署包新增可配置且需持久化保留的 Skill 根目录；文件写入需要路径校验、原子替换及并发保护。
- Agent 的模型调用继续通过现有 `AiModelRouter` 与 `AiInvocationService`，沿用平台模型配置、调用日志和错误规范。
