## REMOVED Requirements

### Requirement: Built-in Seedance model catalogue is available without a new configuration surface
**Reason**: Seedance Endpoint IDs are deployment- and account-specific and must no longer require developers to replace source-owned placeholders.

**Migration**: Preserve the built-in Provider and existing Model identities, add Seedance 2.0 mini, and move each Endpoint ID into authorized platform Model configuration. Models remain fail-closed until configured and enabled.

## ADDED Requirements

### Requirement: Platform administrators manage Seedance Endpoint IDs and constraints
The platform AI configuration interface SHALL allow an authorized platform administrator to save each Seedance Model's Ark Endpoint ID and SHALL expose the source-owned constraint profile used by routing and task validation. Tenant users MUST NOT view or modify Endpoint IDs or Provider credentials.

#### Scenario: Administrator configures a Seedance model
- **WHEN** an authorized platform administrator saves a valid Endpoint ID for one of the four built-in Seedance Models and enables its Provider, Model, and capability
- **THEN** compatible project video tasks can route to that exact Model

#### Scenario: Endpoint ID is missing or a placeholder
- **WHEN** an administrator attempts to enable a Seedance Model whose Endpoint ID is blank or unresolved
- **THEN** the system rejects enablement and the Model remains unavailable to tenant projects

#### Scenario: Tenant requests Model options
- **WHEN** a tenant user loads available project video Models
- **THEN** the system returns only enabled compatible Models and safe constraint metadata without returning Endpoint IDs or Provider credentials
