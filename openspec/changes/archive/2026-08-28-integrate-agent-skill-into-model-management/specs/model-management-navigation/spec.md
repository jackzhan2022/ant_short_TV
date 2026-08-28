## MODIFIED Requirements

### Requirement: Unified model management navigation
The system SHALL replace the separate visible menus for platform Provider, platform Model, model billing, and call logs with one visible menu named "模型管理" under AI service management. It SHALL expose "模型服务商", "AI 大模型", and "调用日志" as internal tabs, subject to their existing view permissions. It SHALL also expose "Agent 管理" and "Skill 管理" as additional internal tabs after "调用日志", subject to the built-in Agent view permission.

#### Scenario: Authorized user opens model management
- **WHEN** a user with one or more relevant view permissions selects "模型管理"
- **THEN** the system SHALL open the first authorized tab and SHALL not show separate visible menus for Provider, Model, billing, logs, Agent, or Skill management

#### Scenario: User has a subset of tab permissions
- **WHEN** a user can view only some of the service-provider, model, call-log, Agent, and Skill capabilities
- **THEN** the system SHALL show only the authorized tabs and SHALL not expose unauthorized tab content through the navigation

#### Scenario: User opens Agent and Skill tabs
- **WHEN** a user with built-in Agent view permission selects "Agent 管理" or "Skill 管理"
- **THEN** the system SHALL render the existing read-only Agent or Skill list and its detail interactions as a same-level model-management tab

### Requirement: Legacy model-management links remain usable
The system SHALL preserve access to legacy service-provider, model, billing, and call-log routes by redirecting them to the model management workspace. The legacy Agent route SHALL also redirect to the model management workspace and SHALL not appear as a visible menu entry.

#### Scenario: User visits the legacy billing link
- **WHEN** an authorized user requests `/ai-service-management/billing`
- **THEN** the system SHALL redirect the user to the model management workspace

#### Scenario: User visits the legacy Agent link
- **WHEN** an authorized user requests `/ai-service-management/agents`
- **THEN** the system SHALL redirect the user to `/ai-service-management/model-management`
