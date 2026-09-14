## MODIFIED Requirements

### Requirement: Unified model management navigation
The system SHALL replace the separate visible menus for platform Provider, platform Model, model billing, and call logs with one visible menu named "模型管理" under AI service management. It SHALL expose "模型服务商", "AI 大模型", and "调用日志" as internal tabs, subject to their existing view permissions. The model-management page SHALL use local component state to control the active visible tab, and selecting a visible tab MUST immediately render its corresponding content without changing the browser URL.

#### Scenario: Authorized user opens model management
- **WHEN** a user with one or more relevant view permissions selects "模型管理"
- **THEN** the system SHALL open the first tab for which that user has view permission and SHALL not show the former four separate menu entries

#### Scenario: User switches a visible tab
- **WHEN** a user selects another visible model-management tab
- **THEN** the system SHALL render that tab's content in the same page without changing the model-management URL

#### Scenario: User has a subset of tab permissions
- **WHEN** a user can view only some of the service-provider, model, and call-log capabilities
- **THEN** the system SHALL show only the authorized tabs and SHALL not expose unauthorized tab content through the navigation

### Requirement: Legacy model-management links remain usable
The system SHALL preserve access to legacy service-provider, model, call-log, and billing routes by redirecting them to the model-management landing page without a tab query parameter. The legacy routes SHALL not appear as visible menu entries.

#### Scenario: User visits the legacy billing link
- **WHEN** an authorized user requests `/ai-service-management/billing`
- **THEN** the system SHALL redirect the user to the model-management landing page without a `tab` query parameter
