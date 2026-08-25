## ADDED Requirements

### Requirement: Platform authorization is independent
The system SHALL assign platform permissions only through platform roles and MUST NOT derive platform permissions from tenant membership, tenant roles, or tenant ownership.

#### Scenario: Tenant owner requests platform management
- **WHEN** a tenant owner without a platform role requests a platform management API
- **THEN** the system returns 403 even though the user owns a tenant

#### Scenario: Platform operator has no tenant
- **WHEN** an authenticated platform operator with the required platform permission requests a platform API without `X-Tenant-Id`
- **THEN** the system authorizes the request without requiring tenant membership

### Requirement: Tenant authorization remains tenant-scoped
The system SHALL evaluate tenant permissions only after validating the requested tenant and active tenant membership. Tenant owner and administrator shortcuts MUST grant tenant permissions only.

#### Scenario: Cross-tenant permission attempt
- **WHEN** a user presents a tenant id for a tenant where the user is not an active member
- **THEN** tenant authorization fails regardless of permissions held in another tenant

### Requirement: Domain-specific guards fail closed
The system SHALL use separate platform, tenant, and project permission guards, and each guard MUST deny access when required scope identifiers, resources, memberships, roles, or permissions are missing or invalid.

#### Scenario: Missing project
- **WHEN** project authorization is evaluated for a project that does not exist in the current tenant
- **THEN** authorization does not grant the requested permission

#### Scenario: Missing fine-grained permission
- **WHEN** an authenticated user reaches a protected method without its required domain permission
- **THEN** the method does not execute and the system returns 403

### Requirement: Permission changes affect subsequent requests
The system SHALL evaluate current role assignments and SHALL NOT treat permissions embedded in an authentication credential as authoritative.

#### Scenario: Platform role revoked
- **WHEN** a platform role is removed from a user with an otherwise valid session
- **THEN** the user's next platform request requiring that role's permission is denied
