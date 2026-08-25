## ADDED Requirements

### Requirement: Bootstrap returns authenticated application context
The system SHALL provide one bootstrap response containing the authenticated user, session expiry, platform roles and permissions, active tenant summaries, selected tenant context when valid, and the selected tenant's roles and permissions.

#### Scenario: Valid selected tenant
- **WHEN** an authenticated user calls bootstrap with an `X-Tenant-Id` for an active membership
- **THEN** the response contains that tenant, membership, tenant roles, tenant permissions, and an enter-workspace next action

#### Scenario: User has no tenants
- **WHEN** an authenticated user with no active tenant memberships calls bootstrap
- **THEN** the response has no tenant context and directs the frontend to create or join a team

### Requirement: Tenant selection is request-scoped
The system MUST use `X-Tenant-Id` as the selected-tenant input and MUST NOT store the current tenant in process memory or server-side user session state.

#### Scenario: Two tabs select different tenants
- **WHEN** two browser tabs for the same user send different valid tenant headers
- **THEN** each request is evaluated against its own tenant without changing the other tab's context

#### Scenario: Backend instance changes
- **WHEN** a tenant-scoped request is routed to a different backend instance
- **THEN** the request resolves the same tenant context solely from the authenticated user and request header

### Requirement: Missing and invalid tenant selections are recoverable
The system SHALL return deterministic onboarding or tenant-selection state without silently retaining permissions from a previous tenant.

#### Scenario: One tenant and no header
- **WHEN** an authenticated user with exactly one active tenant calls bootstrap without a tenant header
- **THEN** bootstrap may return that tenant as the selected context without persisting server-side selection

#### Scenario: Multiple tenants and no header
- **WHEN** an authenticated user with multiple active tenants calls bootstrap without a tenant header
- **THEN** bootstrap returns no selected tenant context and directs the frontend to select a tenant

#### Scenario: Removed tenant membership
- **WHEN** bootstrap receives a tenant header for a membership that has been removed
- **THEN** it returns no tenant permissions and identifies that the selected tenant is unavailable

### Requirement: Frontend context changes atomically
The frontend SHALL use bootstrap for startup and tenant switching and SHALL replace selected tenant, membership, and permissions as one state transition after server validation succeeds.

#### Scenario: Successful tenant switch
- **WHEN** a user selects another accessible tenant
- **THEN** the frontend validates it through bootstrap, atomically applies the returned context, and only then stores the last tenant id

#### Scenario: Failed tenant switch
- **WHEN** bootstrap rejects or cannot resolve a newly selected tenant
- **THEN** the frontend does not expose the new tenant with permissions from the previous tenant

### Requirement: Stateful current-tenant APIs are removed
The system SHALL not expose an API that stores or retrieves a process-local current tenant.

#### Scenario: Legacy current-tenant endpoint
- **WHEN** a client calls the former `/api/tenants/current` contract after migration
- **THEN** no server-side tenant selection is created or returned
