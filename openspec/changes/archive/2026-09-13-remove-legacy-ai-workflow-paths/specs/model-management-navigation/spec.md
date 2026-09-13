## MODIFIED Requirements

### Requirement: Unified model management navigation
The system SHALL expose 模型服务商, AI 大模型, 调用日志, Agent 管理 and Skill 管理 within 模型管理. Agent 管理 SHALL render Workflow Agents and Skill 管理 SHALL render file-backed Skills using their respective current view/edit permissions. Old built-in/editable management modules SHALL be removed and labels SHALL not contain （新）.

#### Scenario: Authorized user opens model management
- **WHEN** a user with relevant view permissions opens 模型管理
- **THEN** the first authorized tab opens without duplicate Agent or Skill tabs

#### Scenario: User has a subset of tab permissions
- **WHEN** the user can view only some capabilities
- **THEN** only authorized tabs appear and unauthorized content is not accessible

#### Scenario: User opens Agent and Skill tabs
- **WHEN** an authorized user opens Agent 管理 or Skill 管理
- **THEN** the current Workflow Agent or file Skill management renders with its independent write permission
- **AND** old built-in permissions do not grant access to these modules

### Requirement: Legacy model-management links remain usable
The system SHALL retain existing redirects for unrelated provider, model, billing and log routes. The retired built-in Agent management route SHALL be removed rather than exposing the old module.

#### Scenario: User visits the legacy billing link
- **WHEN** an authorized user requests /ai-service-management/billing
- **THEN** the system redirects to the model management workspace

#### Scenario: User visits the legacy Agent link
- **WHEN** a user requests /ai-service-management/agents
- **THEN** no legacy Agent page or API is available
