## Why

The current authentication and authorization flow mixes platform-wide privileges with tenant roles, stores the selected tenant in process memory, issues non-revocable tokens, and relies on manually invoked guards while Spring Security permits every API route. Project-level roles are also disconnected from project discovery, and the unused organization model adds fields and permissions without providing a real data boundary.

This change establishes explicit platform, tenant, and project authorization boundaries so access is revocable, horizontally scalable, fail-closed, and understandable to both the backend and frontend.

## What Changes

- Add revocable server-side authentication sessions backed by environment-provided token protection, per-user token versions, account-status checks, and current-device/all-device revocation.
- Separate platform roles and permissions from tenant RBAC. Platform APIs use a platform-only permission guard and tenant owners never inherit platform privileges.
- Replace server-side current-tenant storage with request-scoped `X-Tenant-Id` resolution and a unified authentication bootstrap response containing the user, session, platform permissions, tenants, selected tenant context, and tenant permissions.
- Define one project access model: tenant owners/admins can access every tenant project, while ordinary members can list and access only projects where they have an active project membership.
- Return effective per-project roles, permissions, and capabilities to the frontend so project routes and controls do not infer access from tenant permissions or role names.
- Move authentication into the Spring Security chain, default protected APIs to authenticated access, and retain platform/tenant/project permission annotations for fine-grained authorization.
- **BREAKING** Remove the organization feature, organization permissions, organization APIs/UI, organization tables, project organization fields, and the unused project role data-scope model.
- **BREAKING** Remove legacy authentication and current-tenant APIs after the frontend migrates to the new session and bootstrap contracts.

## Capabilities

### New Capabilities

- `revocable-auth-sessions`: Server-side login sessions, immediate account-status enforcement, current/all-session revocation, and Spring Security authentication.
- `scoped-authorization`: Strict separation of platform, tenant, and project permission domains with fail-closed permission guards.
- `tenant-bootstrap-context`: Stateless request-level tenant selection and one bootstrap contract for all authenticated frontend context.
- `project-access-control`: Project discovery and effective capabilities based on tenant-wide administration or active project membership.
- `organization-free-project-model`: Removal of organization concepts and unused data-scope fields from the tenant and project experience.

### Modified Capabilities

None.

## Impact

- Backend authentication, security configuration, tenant context resolution, RBAC, project services, platform AI management, controllers, error handling, operation logging, and generated OpenAPI contracts.
- Frontend login, logout, initial state, request configuration, tenant switching, access rules, project list/workbench navigation, project forms, organization pages, generated services, and tests.
- Forward-only Flyway migrations for authentication sessions, platform RBAC, token versions, organization removal, and project schema cleanup.
- Existing browser sessions and legacy API clients require migration; a deployment may intentionally require all users to sign in again.
