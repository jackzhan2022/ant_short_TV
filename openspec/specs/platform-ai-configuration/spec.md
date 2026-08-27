# platform-ai-configuration Specification

## Purpose
TBD - created by archiving change retire-legacy-ai-service-config. Update Purpose after archive.
## Requirements
### Requirement: Platform-only AI configuration authority
The system SHALL use platform-managed Provider configurations, Models, and Model Capabilities as the only configuration authority for external AI services. Tenant users MUST NOT create, edit, delete, enable, test, or provide credentials for AI Providers or Models.

#### Scenario: Platform administrator configures a Provider
- **WHEN** a user with the required platform Provider permission saves credentials and enables a Provider
- **THEN** the system stores the protected credentials in the platform Provider configuration and makes them available only to authorized model routing

#### Scenario: Tenant user attempts configuration access
- **WHEN** a tenant user without platform AI configuration permissions requests a Provider or Model management API or page
- **THEN** the system denies access without exposing configuration values or masked credentials

### Requirement: Model-based provider workflow contracts
Every provider-backed AI workflow SHALL identify the requested or resolved platform Model by `modelId` and MUST NOT accept or return a legacy AI service configuration identifier.

#### Scenario: Explicit Model is requested
- **WHEN** an authorized tenant user creates a provider-backed AI task with an enabled and capability-compatible `modelId`
- **THEN** the system persists that Model as the requested and resolved Model for the domain task and shared execution

#### Scenario: No explicit Model is requested
- **WHEN** an authorized tenant user creates a provider-backed AI task without `modelId`
- **THEN** the system resolves the project's configured compatible Model or the enabled platform default compatible Model and persists the resolved Model

#### Scenario: Explicit Model is invalid
- **WHEN** a request specifies a missing, disabled, Provider-disabled, or capability-incompatible Model
- **THEN** the system rejects the request with the canonical configuration error and does not fall back to another Model

### Requirement: Unified provider routing and invocation
All production provider contact SHALL pass through `AiModelRouter` and `AiInvocationService`. Business workflow services MUST NOT read Provider credentials directly, read legacy service configuration, or issue direct provider HTTP calls.

#### Scenario: Provider-backed execution starts
- **WHEN** an execution handler performs a provider-backed phase
- **THEN** the router validates the Model, Capability, Provider, and Provider configuration before the invocation service calls the registered adapter

#### Scenario: Provider configuration is unavailable
- **WHEN** the resolved Provider configuration is missing, disabled, or lacks usable credentials
- **THEN** the system fails before provider contact and records no provider-success log, usage cost, or point settlement

### Requirement: Local-only operations remain outside AI configuration
An operation that does not contact an external AI provider MUST NOT require a Provider configuration or Model and MUST NOT create provider invocation, usage-cost, or point-settlement records.

#### Scenario: Local voice placeholder is created
- **WHEN** the current voice placeholder workflow produces its deterministic local output
- **THEN** it completes without `serviceConfigId`, `modelId`, Provider routing, AI call logs, provider cost, or point consumption

### Requirement: Legacy AI service configuration is absent
The final application and database schema MUST NOT expose or depend on tenant AI service configuration, legacy model linkage, or service-configuration connectivity-test persistence.

#### Scenario: Application starts after cleanup migration
- **WHEN** the cleanup migration has completed and the application starts
- **THEN** no legacy AI service configuration table, API, page, permission, runtime mapper, routing fallback, or `legacyServiceConfigId` remains

#### Scenario: Legacy development configuration exists before migration
- **WHEN** the cleanup migration encounters legacy service configurations and Models derived from them
- **THEN** it discards those configurations and derived Models, clears affected references safely, and does not migrate their credentials into the surviving platform configuration

### Requirement: Platform credentials require explicit reconfiguration
The cleanup migration SHALL clear stored Provider credentials and disable Provider configurations whose credential provenance cannot be distinguished from legacy synchronization. The system MUST remain fail-closed until a platform administrator explicitly configures and enables them.

#### Scenario: AI request occurs immediately after migration
- **WHEN** a provider-backed AI request is made before a platform administrator has reconfigured and enabled its Provider
- **THEN** the system rejects the request before provider contact with the canonical Provider configuration error

#### Scenario: Platform administrator completes reconfiguration
- **WHEN** a platform administrator saves valid Provider credentials, passes connectivity validation, and enables the required Provider and Model
- **THEN** compatible provider-backed workflows can resolve and invoke that Model

### Requirement: Configuration and tenant usage permissions are separate
Platform configuration authority SHALL use platform Provider and Model permissions. Tenant AI execution SHALL continue to require `AI_SERVICE:USE`, and tenant call-log access SHALL use a log-specific permission that grants no configuration authority.

#### Scenario: Tenant can use AI without managing configuration
- **WHEN** a tenant member has the required business permission and `AI_SERVICE:USE` but no platform AI permission
- **THEN** the member can execute the permitted workflow using platform Models but cannot access Provider or Model management

#### Scenario: Tenant can inspect its call logs
- **WHEN** a tenant member has the tenant call-log view permission
- **THEN** the member can view only tenant-scoped invocation and execution data without gaining access to Provider credentials or configuration controls

