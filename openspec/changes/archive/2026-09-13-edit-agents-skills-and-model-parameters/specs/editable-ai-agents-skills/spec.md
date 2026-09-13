## ADDED Requirements

### Requirement: Agent and Skill definitions are editable and versioned
管理员 MUST 能编辑 Agent 和 Skill 的可配置字段，并以草稿、已发布版本保存；每个 code 同时只能有一个已发布版本。

#### Scenario: Publish an Agent revision
- **WHEN** an authorized administrator saves and publishes a valid Agent revision
- **THEN** the system persists a new version, marks it active, and uses it for newly created AI tasks

#### Scenario: Reject unauthorized edit
- **WHEN** a non-administrator attempts to create, edit, publish, or rollback a definition
- **THEN** the API rejects the request with a permission error and leaves the active version unchanged

### Requirement: Skills can be associated with Agents
系统 MUST 支持为 Agent 维护关联 Skill 列表，调用时按已发布版本解析关联内容；停用的 Skill MUST NOT 被注入新请求。

#### Scenario: Disabled Skill is excluded
- **WHEN** a Skill associated with an Agent is disabled before a new task starts
- **THEN** the task snapshot excludes that Skill and records the resulting configuration version

### Requirement: Analysis tasks snapshot AI definitions
系统 MUST 在分析任务启动时保存 Agent、Skill 和模型参数的版本快照，任务重试和后续阶段 MUST 使用相同快照。

#### Scenario: Configuration changes during retry
- **WHEN** an administrator publishes a new prompt after a task has failed
- **THEN** retrying that task uses its original snapshot unless the user explicitly starts a new task
