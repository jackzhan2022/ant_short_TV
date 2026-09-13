## MODIFIED Requirements

### Requirement: Unified model management navigation
The system SHALL replace the separate visible menus for platform Provider, platform Model, model billing, and call logs with one visible menu named "模型管理" under AI service management. It SHALL expose "模型服务商", "AI 大模型", and "调用日志" as internal tabs, subject to their existing view permissions. It SHALL also expose the existing "Agent 管理" and "Skill 管理" tabs after "调用日志", subject to the built-in Agent view permission, followed by the independent "Agent（新）" and "Skill（新）" tabs subject to their respective new view permissions.

#### Scenario: Authorized user opens model management
- **WHEN** a user with one or more relevant view permissions selects "模型管理"
- **THEN** the system SHALL open the first authorized tab and SHALL not show separate visible menus for Provider, Model, billing, logs, existing Agent, existing Skill, Agent（新）, or Skill（新）management

#### Scenario: User has a subset of tab permissions
- **WHEN** a user can view only some of the service-provider, model, call-log, existing Agent, existing Skill, Agent（新）, and Skill（新）capabilities
- **THEN** the system SHALL show only the authorized tabs in their defined relative order and SHALL not expose unauthorized tab content through the navigation

#### Scenario: User opens existing Agent and Skill tabs
- **WHEN** a user with built-in Agent view permission selects "Agent 管理" or "Skill 管理"
- **THEN** the system SHALL render the existing Agent or Skill list and its existing detail interactions as a same-level model-management tab

#### Scenario: User opens new Agent and Skill tabs
- **WHEN** a user has the corresponding new module view permission and selects "Agent（新）" or "Skill（新）"
- **THEN** the system SHALL render the independent editable module without replacing, mutating, or routing through the existing Agent and Skill module
