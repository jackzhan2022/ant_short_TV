## 1. Baseline and Contract Tests

- [x] 1.1 Add backend integration tests that inventory explicitly public APIs and prove representative tenant, project, and platform APIs reject anonymous requests
- [x] 1.2 Add negative authorization tests for cross-tenant access, tenant-owner platform access, missing projects, inactive memberships, and missing permissions
- [x] 1.3 Add frontend tests that capture current login bootstrap, tenant switching, project navigation, and organization UI behavior before migration

## 2. Authentication and Authorization Schema

- [x] 2.1 Add a forward Flyway migration for `app_user.token_version` and indexed `auth_session` storage with expiry and revocation metadata
- [x] 2.2 Add platform role, permission, role-permission, and user-role tables with required uniqueness and lookup indexes
- [x] 2.3 Add an idempotent controlled bootstrap mechanism for assigning the initial platform administrator role from runtime configuration
- [x] 2.4 Add mapper tests and schema migration assertions for session and platform RBAC tables and indexes

## 3. Revocable Session Authentication

- [x] 3.1 Add validated authentication configuration for token pepper, session TTL, cookie security, and activity-update throttling
- [x] 3.2 Implement opaque credential generation, HMAC token hashing, session issuance, indexed session/user lookup, expiry validation, and throttled activity updates
- [x] 3.3 Update login and registration to create sessions and set Secure HttpOnly SameSite cookies without returning reusable credentials in response bodies
- [x] 3.4 Implement current-session logout and all-session invalidation through revocation and user token-version rotation
- [x] 3.5 Add session service tests for valid login, invalid hash, expiry, revocation, disabled/deleted user, token-version mismatch, current logout, and logout-all

## 4. Spring Security Boundary

- [x] 4.1 Implement `AuthenticatedUser`, the session authentication filter, and a `CurrentPrincipal` adapter backed by Spring SecurityContext
- [x] 4.2 Add standard JSON `AuthenticationEntryPoint` and `AccessDeniedHandler` responses for 401 and 403
- [x] 4.3 Configure SPA-compatible CSRF token issuance and validation for unsafe cookie-authenticated requests
- [x] 4.4 Replace broad `/api/**` permit-all rules with an explicit public allowlist and authenticated-by-default API rules
- [x] 4.5 Enable method security and add integration tests proving filters run before controller logic and CSRF rejects unsafe requests without a valid token

## 5. Scoped Platform and Tenant Authorization

- [x] 5.1 Implement platform RBAC mappers/services and current platform-role/permission lookup without tenant dependencies
- [x] 5.2 Implement `PlatformPermissionGuard` and `@RequirePlatformPermission`, then migrate every platform AI management endpoint to it
- [x] 5.3 Split permission catalogs so tenant owner/admin shortcuts exclude all platform permissions
- [x] 5.4 Add tenant-wide `PROJECT:VIEW_ALL`, `PROJECT:EDIT_ALL`, and `PROJECT:DELETE_ALL` permissions and migrate existing tenant-role project grants without changing project-role permissions
- [x] 5.5 Implement explicit tenant and project permission guards that receive scope identifiers directly and fail closed for missing or invalid context
- [x] 5.6 Add tests proving platform operators work without a tenant, tenant owners cannot access platform APIs, and role revocation affects the next request

## 6. Stateless Tenant Bootstrap

- [x] 6.1 Implement request-scoped tenant resolution from authenticated principal plus `X-Tenant-Id`, including tenant/member status validation
- [x] 6.2 Implement the bootstrap response contract for user, session, platform access, tenant summaries, selected tenant access, unavailable-selection reason, and next action
- [x] 6.3 Add bootstrap tests for zero, one, and multiple tenants; valid and removed selections; two independent tenant headers; and requests routed without process-local state
- [x] 6.4 Migrate backend services from `CurrentUserHolder` and stored current-tenant fallback to `CurrentPrincipal` and explicit request tenant context

## 7. Organization and Data-Scope Removal

- [x] 7.1 Remove organization controllers, services, entities, mappers, requests, responses, statuses, permissions, and organization-specific errors from backend code
- [x] 7.2 Remove organization identifiers and names from project/project-member entities, requests, responses, queries, and service logic
- [x] 7.3 Remove `ProjectDataScope`, project role `dataScope` contracts, and the unused `project_role.data_scope` behavior from backend code
- [x] 7.4 Add a forward Flyway migration that drops organization tables and project organization/data-scope columns while preserving tenant, project, and membership rows
- [x] 7.5 Remove organization routes, pages, services, types, access flags, locale entries, and all organization fields and filters from frontend project workflows
- [x] 7.6 Replace organization-focused tests with migration and project CRUD/member tests that assert organization-free contracts

## 8. Unified Project Access

- [x] 8.1 Implement `ProjectAccessContext` and `ProjectAccessResolver` for tenant-wide administrators and active project members with active roles
- [x] 8.2 Replace generic project permission inference and duplicated service checks with the project access resolver across project, script, AI image/video, shot, review, and workbench APIs
- [x] 8.3 Implement accessible-project queries that return all tenant projects for `VIEW_ALL` users and only active memberships for ordinary members
- [x] 8.4 Extend project list/detail contracts with access source, project role, effective permissions, and capability booleans generated from the resolved access context
- [x] 8.5 Add backend tests for owner/admin access, custom `VIEW_ALL`, assigned-member discovery, removed membership, direct non-member access, cross-tenant ids, role status, and capability/action consistency

## 9. Frontend Session, Bootstrap, and Project Migration

- [x] 9.1 Replace browser access-token storage and authorization headers with cookie credentials and CSRF request handling
- [x] 9.2 Replace separate current-user, tenant-list, and permission loading with one typed bootstrap client and initial-state model
- [x] 9.3 Make login, registration, logout, startup, and tenant switching use session/bootstrap contracts and apply tenant context atomically
- [x] 9.4 Update access rules so any active tenant member can open the project list while project creation and tenant-wide actions use tenant permissions
- [x] 9.5 Update project lists, detail pages, and production workbench controls to use per-project capabilities and render backend 403 responses for unauthorized dynamic routes
- [x] 9.6 Add frontend tests for no/one/multiple tenant bootstrap, failed and successful switches, platform-only navigation, assigned-project discovery, and capability-based controls

## 10. Legacy Cleanup and Verification

- [x] 10.1 Remove `/api/currentUser`, `/api/login/account`, `/api/login/outLogin`, `/api/user/me`, `/api/auth/permissions`, and `/api/tenants/current` after all callers migrate
- [x] 10.2 Remove `CurrentTenantStore`, the custom access-token service/filter, `CurrentUserHolder`, and remaining compatibility code
- [x] 10.3 Update the OpenAPI source and regenerate frontend services without manually editing generated files
- [x] 10.4 Add scheduled cleanup for expired/revoked sessions and verify activity timestamps are not written on every request
- [x] 10.5 Run backend authorization/session/migration tests, the full backend suite, frontend tests, TypeScript/Biome lint, Ant Design lint, and the production build
- [x] 10.6 Run strict OpenSpec validation and document the forced re-login, destructive organization migration backup, required secrets, initial platform-admin bootstrap, and rollback boundary in release notes
