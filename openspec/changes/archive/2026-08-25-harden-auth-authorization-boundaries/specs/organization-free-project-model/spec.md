## ADDED Requirements

### Requirement: Organization functionality is absent
The system SHALL not expose organization management pages, routes, APIs, permissions, roles, membership workflows, or organization-specific error behavior.

#### Scenario: Tenant navigation after removal
- **WHEN** an authenticated tenant member loads team navigation
- **THEN** no organization management entry is displayed

#### Scenario: Former organization API
- **WHEN** a client requests a former organization endpoint
- **THEN** the endpoint is not available as a supported tenant API

### Requirement: Projects do not reference organizations
Project and project-member persistence, requests, responses, forms, filters, and generated contracts MUST NOT contain organization identifiers or organization names.

#### Scenario: Create project
- **WHEN** an authorized user creates a project
- **THEN** the request and resulting project contain no organization assignment

#### Scenario: Manage project member
- **WHEN** an authorized user adds or updates a project member
- **THEN** the membership is defined without an organization field

### Requirement: Project roles have no unused data scope
The system SHALL remove project role data-scope values and SHALL express project behavior exclusively through project role permissions and tenant-wide project permissions.

#### Scenario: Create project role
- **WHEN** an authorized user creates a project role
- **THEN** the role contract accepts permissions but no `ALL`, `ORGANIZATION`, or `PROJECT` data-scope value

### Requirement: Organization storage is removed by forward migration
The database SHALL remove organization tables and project organization/data-scope columns through a new forward-only migration without modifying previously applied migrations.

#### Scenario: Migrate an existing database
- **WHEN** the new migration runs against a database containing the existing organization schema
- **THEN** organization tables and related project columns are removed while tenant, project, and project membership records remain valid
