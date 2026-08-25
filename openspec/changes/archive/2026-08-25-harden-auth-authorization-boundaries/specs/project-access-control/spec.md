## ADDED Requirements

### Requirement: Project discovery follows effective access
The system SHALL list every project in the selected tenant for tenant owners, tenant administrators, and users with tenant-wide project view permission. Other active tenant members SHALL see only projects where they have an active project membership.

#### Scenario: Tenant administrator lists projects
- **WHEN** a tenant administrator requests the project list
- **THEN** the response includes every non-deleted project in that tenant

#### Scenario: Ordinary member lists projects
- **WHEN** an ordinary tenant member assigned only to project A requests the project list
- **THEN** the response includes project A and excludes other tenant projects

#### Scenario: Removed project member lists projects
- **WHEN** a user's project membership is removed
- **THEN** that project is absent from the user's next accessible-project list unless the user has tenant-wide access

### Requirement: Project access is resolved consistently
The system SHALL resolve project access from tenant-wide permissions or an active project membership and active project role. Project existence and tenant ownership MUST be validated before permissions are granted.

#### Scenario: Direct access by non-member
- **WHEN** an ordinary tenant member directly requests a project where the user has no active project membership
- **THEN** the system returns 403 and does not disclose project content

#### Scenario: Cross-tenant project identifier
- **WHEN** a user supplies a project identifier belonging to a different tenant context
- **THEN** project authorization fails closed

### Requirement: Project responses expose effective capabilities
Project list and detail responses SHALL include the current user's access source, project role when applicable, effective project permissions, and stable capability values generated from the same access context used for enforcement.

#### Scenario: Project member receives capabilities
- **WHEN** a project member with a writer role loads the project
- **THEN** the response identifies the writer role and exposes only capabilities allowed by the role's effective permissions

#### Scenario: Tenant-wide administrator receives capabilities
- **WHEN** a tenant administrator loads a project without a project membership
- **THEN** the response identifies tenant-wide access and exposes the administrator's effective project capabilities

### Requirement: Tenant-wide and project permissions are distinguishable
The system SHALL distinguish tenant-wide project administration permissions from permissions granted by a project role.

#### Scenario: Tenant-wide view permission
- **WHEN** a tenant role grants `PROJECT:VIEW_ALL`
- **THEN** the user can discover and view all projects in that tenant without an individual project membership

#### Scenario: Project role view permission
- **WHEN** a project role grants `PROJECT:VIEW`
- **THEN** the permission applies only to the project membership carrying that role

### Requirement: Frontend project authorization uses project context
The frontend SHALL allow any active tenant member to open the accessible-project list, use tenant permissions for project creation and tenant-wide actions, and use per-project capabilities for project controls and workbench access.

#### Scenario: Project-only member enters workbench
- **WHEN** an ordinary member receives a listed project with view capability and opens its workbench
- **THEN** the route is accessible and its controls follow the returned project capabilities

#### Scenario: Unauthorized dynamic project route
- **WHEN** an authenticated tenant member opens a project route without project access
- **THEN** the frontend presents the authorization failure returned by the project context request

### Requirement: Review drafts have an explicit pre-project state
The system SHALL allow a script-review import to remain unbound before a main project is selected. An unbound review draft SHALL be accessible only to its creator or a tenant-wide project administrator. Once bound, all review resources SHALL use the owning main project's effective permissions.

#### Scenario: Creator imports an unbound review draft
- **WHEN** an active tenant member imports a script without selecting a main project
- **THEN** the review draft is created with no main project and is visible to its creator

#### Scenario: Another ordinary member lists review drafts
- **WHEN** another ordinary tenant member lists review drafts
- **THEN** the creator's unbound review draft is excluded

#### Scenario: Review draft is bound to a project
- **WHEN** the creator binds a review draft to a main project they can edit
- **THEN** subsequent review operations are authorized from that main project's access context
