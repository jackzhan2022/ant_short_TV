## Context

“模型管理”目前包含模型服务商、AI 大模型、调用日志、Agent 管理和 Skill 管理五个页签。既有 Agent/Skill 表及接口承担内置分析流程配置，直接扩展会增加现有生产调用链的回归风险。新需求把 Agent 定义为可调用上游数据和工具的纯文本工作流，把 Skill 定义为可被 Agent 按序加载的方法论文件；两者都需要新增、编辑并保存即生效，但当前阶段明确不做版本管理。

Skill 文件与后端服务属于同一项目部署。生产环境必须让运行进程可写 Skill 根目录，并在发布切换时保留该目录，否则管理页面保存的内容会被新发布包覆盖。Agent 的外部模型调用仍受平台模型、权限、租户作用域、统一调用日志和计费链路约束。

## Goals / Non-Goals

**Goals:**

- 在保留旧模块和旧运行链不变的情况下建立独立的 Agent（新）和 Skill（新）管理面。
- 支持 Agent/Skill 新增、编辑、复制、引用保护删除和保存即生效。
- 以显式关联而非提示词文本决定 Agent 可加载的 Skill 和可执行的工具。
- 通过现有统一模型路由实现可限制步数、可审计、受作用域约束的原生 Tool Calling 循环。
- 为后续业务场景逐个绑定新 Agent 提供稳定接口，但不在本变更中替换已有业务 Agent。

**Non-Goals:**

- 不迁移、删除或改变现有“Agent 管理”“Skill 管理”及其表、API、权限和调用链。
- 不实现 Agent 或 Skill 的草稿、发布、历史版本、差异比较和回滚。
- 不提供在线编写后端工具代码或修改工具 Schema 的能力。
- 不把模型本身可完成的改写、总结等推理动作包装成工具，也不解析提示词中的步骤来强制编排。
- 不允许模型自由指定租户、项目、剧集或用户作用域。

## Decisions

### 1. 使用独立领域模型和 API

新增 `ai_workflow_agent`、`ai_workflow_agent_skill`、`ai_workflow_agent_tool`、`ai_workflow_agent_run` 和 `ai_workflow_agent_run_step`，使用独立服务与 `/api/platform/ai/workflow-agents`、`/workflow-skills`、`/agent-tools`、`/workflow-agent-runs` API。Agent code 创建后不可修改；Skill 和 Tool 关联在一次事务内全量替换，并使用更新令牌或时间戳做并发控制。

选择独立模型是为了隔离现有内置 Agent 的语义和发布风险。复用旧表虽然初期字段较少，但会把“内置模板配置”和“用户可编辑工作流”两个生命周期耦合，故不采用。

### 2. Skill 以文件为唯一内容来源

Skill 根目录通过配置项提供，开发环境默认指向项目 `skills/`，每个 Skill 固定映射到 `<root>/<code>/SKILL.md`。后端从目录扫描列表并解析 frontmatter，不在数据库重复保存正文；Agent-Skill 关联只保存不可变 code。写入流程为：校验 code 与规范化路径、解析完整内容、核对 revision hash、写同目录临时文件、原子替换目标文件。

运行时在构造 Agent 时读取当前文件，或在保存后主动失效按 code 缓存，确保修改无需重启即可生效。相比把正文存数据库，这保持了 Skill 文件的可部署、可检查特性；代价是部署必须将 Skill 根目录作为持久化目录保留。部署脚本需先备份并复用现有根目录，不得用发布包默认内容覆盖在线编辑结果。

### 3. Agent 提示词保持纯文本，关联提供真实授权

Agent 保存 system prompt 原文。编辑器工具选择器同时完成两件事：加入工具关联白名单；在光标处插入包含稳定 tool code 的可读工作流文字。系统不引入 `{{tool:...}}` 占位符，也不从提示词扫描或推导授权。Skill 内容按关联顺序拼接到 system prompt 的独立边界块中，避免名称碰撞并便于生成运行快照。

这种方式让编写体验接近自然语言工作流，同时安全边界由结构化关联决定。采用解析 DSL 会提高确定性，但与当前“由模型理解并直接调用工具”的产品定义不符，故暂不采用。

### 4. 工具注册表由后端代码维护

定义统一 ToolDefinition：code、展示信息、输入/输出 JSON Schema、风险级别、失败策略和 executor。注册表通过依赖注入组装，只读 API 暴露元数据。executor 接受后端生成的 ExecutionContext 和已校验业务参数；tenantId、userId、projectId、episodeId、taskId 等作用域字段不由模型决定。

首批工具按实现能力注册：`read_project_context`、`list_episode_scripts`、`read_episode_script`、`read_adjacent_episodes`、`read_script_analysis`、`read_script_assets`、`validate_screenplay_format` 和 `save_episode_script`。未完成的工具不得仅以元数据占位。写工具使用现有领域服务及权限校验；`save_episode_script` 在单一数据库事务中创建版本并切换 current version。

### 5. 执行器使用统一模型服务的原生 Tool Calling

运行器加载 Agent 当前配置、按序读取 Skill、解析白名单工具并构造消息与 tool schemas。每轮通过 `AiModelRouter` 和 `AiInvocationService` 调用选定模型；收到 tool call 后依次进行白名单、JSON Schema、权限和作用域校验，再执行工具并把规范化结果回送模型。循环在 final response、maxSteps 或终止错误处结束。

业务正式调用只接受已保存且启用的 Agent code。管理页测试接口可携带临时配置 DTO，但仍走相同验证和执行器，并标记 TEST；临时配置不会写入 Agent 表。这样可在“保存即上线”的约束下先验证修改。

直接由 Agent 服务调用 Provider 会绕过统一鉴权、错误、日志和成本核算，故明确禁止。若现有 invocation DTO 尚不支持工具消息，需要在其内部合同上增加类型化 tool definitions、tool calls 和 tool results，同时保持现有文本调用兼容。

### 6. 运行快照代替配置版本

虽然当前不做配置版本，`ai_workflow_agent_run` 仍保存运行类型、作用域、Agent prompt、模型参数、Skill code/content/hash、tool codes、最终输出和错误；step 表保存每轮模型调用引用、工具输入、脱敏输出、耗时和状态。配置快照用于回答“这次实际执行了什么”，不作为可恢复的配置版本，也不提供回滚入口。

### 7. 导航与权限保持并行

模型管理页签顺序固定为：模型服务商、AI 大模型、调用日志、Agent 管理、Skill 管理、Agent（新）、Skill（新）。新增独立的 Agent view/edit 与 Skill view/edit 权限，后端逐端点校验；前端仅用权限控制可见性和按钮状态，不把隐藏视为安全边界。旧权限不自动授予新模块写能力，权限迁移可为平台超级管理员授予新权限。

## Risks / Trade-offs

- [Skill 位于发布项目内，部署可能覆盖在线修改] → 将 Skill 根目录配置为发布间持久化路径；部署前备份，发布时复用或挂载，禁止无条件同步默认文件。
- [保存即生效导致错误配置立即影响运行] → 提供不落库的测试运行、严格保存校验、乐观锁和禁用开关；正式运行保留完整快照。
- [无配置版本无法直接回滚] → 当前按产品决定接受；运行快照仅用于审计。后续版本化可基于现有稳定 code 和快照设计扩展。
- [Skill 修改会同时影响多个 Agent] → 保存页明确展示引用 Agent 和即时影响提示，并用 revision 冲突保护避免覆盖他人修改。
- [Tool Calling 可能循环或产生大日志] → 强制 maxSteps、单步/总超时、参数和结果大小限制，日志按既有策略脱敏。
- [模型伪造作用域或调用未授权工具] → 工具集合只来自结构化白名单，作用域只从服务端 ExecutionContext 注入，执行前再次做领域权限校验。
- [文件系统与数据库关联短暂不一致] → Skill code 不可变；删除前先检查引用，写入使用原子替换；部署健康检查校验所有关联 Skill 可读且有效。

## Migration Plan

1. 新增数据库表、唯一键、外键、运行状态索引和四项独立权限，并仅向平台超级管理员角色初始化授权。
2. 配置并部署持久化 Skill 根目录，执行路径可写性、原子替换和已有文件格式的启动健康检查。
3. 部署后端只读工具目录、Skill API、Agent CRUD/关联 API、测试与正式运行 API；此时不建立任何旧业务绑定。
4. 部署前端两个新页签及权限控制，保留所有旧页签、路由和接口。
5. 创建示例 Skill/Agent 并在限定项目中完成读取、校验、保存新剧集版本和审计日志的烟雾测试。

回滚时先隐藏并禁用新页签和正式运行入口，再回滚应用版本。新表和持久化 Skill 文件保留，避免数据丢失；数据库结构在确认无需恢复配置后再单独清理。旧模块始终保持可用，因此回滚不要求迁移业务数据。

## Open Questions

- 首批业务场景何时从旧 Agent 绑定迁移到新 Agent 不属于本变更，需后续为每个场景单独提案和灰度。
