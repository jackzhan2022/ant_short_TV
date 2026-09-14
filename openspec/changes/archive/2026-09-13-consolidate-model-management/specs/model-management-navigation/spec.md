## ADDED Requirements

### Requirement: Unified model management navigation
The system SHALL replace the separate visible menus for platform Provider, platform Model, model billing, and call logs with one visible menu named "模型管理" under AI service management. It SHALL expose "模型服务商", "AI 大模型", and "调用日志" as internal tabs, subject to their existing view permissions.

#### Scenario: Authorized user opens model management
- **WHEN** a user with one or more relevant view permissions selects "模型管理"
- **THEN** the system SHALL open the first tab for which that user has view permission and SHALL not show the former four separate menu entries

#### Scenario: User has a subset of tab permissions
- **WHEN** a user can view only some of the service-provider, model, and call-log capabilities
- **THEN** the system SHALL show only the authorized tabs and SHALL not expose unauthorized tab content through the navigation

### Requirement: Model management labels
The system SHALL use "模型服务商" as the user-visible label for platform Provider management and "AI 大模型" as the user-visible label for platform Model management in the model management tabs, page titles, table headings, forms, and actions affected by this change.

#### Scenario: User views configuration tabs
- **WHEN** a user opens the service-provider or AI-model tab
- **THEN** the system SHALL display the corresponding new Chinese label and SHALL not display the former "平台 Provider" or "平台 Model" labels in that management flow

### Requirement: Legacy model-management links remain usable
The system SHALL preserve access to legacy service-provider, model, and call-log routes by redirecting them to the corresponding authorized model management tab. The legacy billing route SHALL redirect to the AI-model tab and SHALL not appear as a visible menu entry.

#### Scenario: User visits the legacy billing link
- **WHEN** an authorized user requests `/ai-service-management/billing`
- **THEN** the system SHALL redirect the user to the AI-model tab in model management
