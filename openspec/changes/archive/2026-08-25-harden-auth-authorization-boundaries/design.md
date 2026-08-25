## Context

The backend currently issues a custom seven-day signed bearer token using a source-code secret, stores the selected tenant in both browser local storage and a process-local map, and permits all `/api/**` routes in Spring Security. Authentication and authorization are then enforced inconsistently through `CurrentUserHolder`, service calls, and a generic permission aspect that infers tenant and project identifiers from request paths and method arguments.

Tenant RBAC contains platform-wide AI management permissions, so a tenant owner receives platform privileges through the owner shortcut. Project roles exist, but tenant-level permission loading controls frontend project navigation and the project list returns every tenant project. The organization schema and UI exist without an operational membership workflow or data-scope enforcement.

The application is a same-origin browser SPA backed by Spring Boot and MySQL. The design must preserve tenant isolation, support multiple backend instances and browser tabs, keep authorization changes promptly effective, and avoid adding an external identity provider or distributed cache before they are needed.

## Goals / Non-Goals

**Goals:**

- Make authentication sessions revocable and immediately enforce user status changes.
- Give platform, tenant, and project authorization separate data models and guards.
- Make tenant selection request-scoped and return frontend startup context through one contract.
- Ensure users discover exactly the projects they can access and receive effective per-project capabilities.
- Put authentication and coarse API protection in Spring Security while retaining explicit domain authorization.
- Remove the unused organization and project data-scope concepts completely.

**Non-Goals:**

- Integrating OAuth, OIDC, SSO, Keycloak, or third-party API clients.
- Implementing organization hierarchy, organization-scoped permissions, or row-level organization filtering.
- Adding Redis or another distributed permission cache before measurement demonstrates a need.
- Redesigning unrelated business permissions or project production workflows.
- Preserving compatibility with generated legacy Ant Design Pro authentication endpoints indefinitely.

## Decisions

### Use opaque server-side sessions for browser authentication

Add `app_user.token_version` and an `auth_session` table containing a session id, user id, HMAC token hash, captured token version, status, expiry, revocation metadata, timestamps, IP, and user agent. Generate at least 256 bits of random token material and protect stored hashes with an environment-provided `AUTH_TOKEN_PEPPER`; startup fails when production configuration omits or weakens this secret.

The browser receives the raw token only in a Secure, HttpOnly, SameSite cookie. Spring Security validates the session, expiry, user status, deletion state, and token-version match on every authenticated request. Logout revokes the current row; password reset, administrative disablement, or logout-all increments `token_version` and invalidates all prior sessions. `last_seen_at` updates are throttled to avoid a write per request.

An opaque session is preferred over another custom JWT because immediate revocation and account-status enforcement already require server-side state. It avoids JWT blacklist complexity and keeps credentials out of browser JavaScript. Cookie authentication requires CSRF protection for unsafe methods, using Spring's SPA-compatible CSRF token repository and request header validation.

### Make Spring Security the authentication boundary

The session authentication filter creates an `AuthenticatedUser` principal in `SecurityContext`; application code no longer owns a parallel `ThreadLocal`. A small `CurrentPrincipal` adapter may isolate services from direct static `SecurityContextHolder` access.

The security chain explicitly permits login, registration, verification, confirmed public resources, health endpoints, and development-only API documentation. Other `/api/**` routes require authentication by default, unknown protected routes fail closed, and standardized JSON entry-point/denied handlers return 401 and 403 responses. Method security and domain guards remain responsible for platform, tenant, and project authorization.

This layered approach is preferred over either URL rules alone, which cannot express resource ownership, or permission annotations alone, which make one missing annotation expose an API.

### Separate platform RBAC from tenant RBAC

Add `platform_role`, `platform_permission`, `platform_role_permission`, and `platform_user_role`. Platform endpoints use `@RequirePlatformPermission` backed by `PlatformPermissionGuard`, which reads only the authenticated user and platform role assignments. It does not require or inspect `X-Tenant-Id`, `tenant_member`, or tenant owner status.

Remove all `PLATFORM_*` definitions from tenant permissions. Tenant owner and admin shortcuts grant only tenant permissions. Platform permission changes are evaluated from current database state; permissions are not embedded in the session token.

Separate tables are preferred over a scope column in the existing tenant role model because they make cross-scope assignments structurally impossible and allow platform operators who are not tenant members.

### Make tenant context stateless and bootstrap it once

Remove `CurrentTenantStore` and treat `X-Tenant-Id` as the sole selected-tenant input for tenant-scoped requests. Tenant resolution always verifies the authenticated user, tenant status, and active tenant membership.

Add `GET /api/auth/bootstrap`, accepting an optional `X-Tenant-Id`, and return the user, session expiry, platform roles/permissions, active tenant summaries, selected tenant/member/tenant permissions when valid, and a deterministic next action. With no header, a single active tenant may be selected in the response without server persistence; multiple tenants require selection; no tenants require create-or-join onboarding. An invalid or removed selection returns a recoverable null tenant context and reason rather than silently retaining old permissions.

The frontend uses the same bootstrap operation for startup and tenant switching. It validates a newly selected tenant before committing the last tenant id locally and atomically replaces all initial-state authorization fields. The server does not expose a state-changing current-tenant endpoint.

### Resolve project access through one project-aware service

Introduce `ProjectAccessResolver` returning a `ProjectAccessContext` with tenant, project, user, access source, optional project membership/role, and effective permissions. Tenant owner/admin or a custom tenant role with `PROJECT:*_ALL` receives tenant-wide project access. Other active tenant members must have an active `project_member` row and receive permissions from the active project role.

Use distinct tenant permissions such as `PROJECT:VIEW_ALL`, `PROJECT:EDIT_ALL`, and `PROJECT:DELETE_ALL`; keep `PROJECT:CREATE` tenant-scoped and project role permissions resource-scoped. Project existence, tenant ownership, membership, role state, and permissions always fail closed. Project guards receive an explicit project id expression or argument rather than parsing arbitrary URI segments or selecting the first `Long` argument.

Project list queries return all tenant projects for tenant-wide viewers and join active project membership for ordinary members. List and detail responses include access source, project role, effective permissions, and stable capability booleans. The frontend opens the project list for any active tenant member, uses tenant permissions for create/all-project actions, and uses response capabilities for project controls. Dynamic project pages load project context and render 403 when access is absent instead of requiring all project permissions in bootstrap.

Script-review imports may begin as unbound personal review drafts. An unbound `review_project` has a null `main_project_id` and is visible and mutable only to its creator or a tenant-wide project administrator. Binding a review draft requires edit access to the target main project; after binding, every review project, task, issue, history, and export operation resolves the owning main project and uses `ProjectAccessResolver` through `ProjectPermissionGuard`. Rebinding is not supported by the normal workflow, preventing a review resource from silently moving between project authorization domains.

### Remove organization and project data-scope concepts

Delete organization backend types, endpoints, services, mappers, permissions, error codes, frontend routes/pages/services/locales/access flags, and organization fields from project forms and responses. A forward Flyway migration drops `organization_member`, `organization`, `project.organization_id`, and `project_member.organization_id`.

Also remove `project_role.data_scope` and `ProjectDataScope`. The value is not enforced today and project roles are inherently scoped to one project; project permissions express all remaining behavior more accurately. Existing Flyway migrations remain immutable.

### Remove legacy contracts after consumers migrate

Migrate the SPA to the new session and bootstrap contracts before removing `/api/currentUser`, `/api/login/account`, `/api/login/outLogin`, `/api/user/me`, `/api/auth/permissions`, and `/api/tenants/current`. Update the OpenAPI source and regenerate clients rather than manually editing generated services. Remove the custom access-token parser, `CurrentUserHolder`, and compatibility code only after no active route depends on them.

## Risks / Trade-offs

- [Existing sessions cannot be converted safely] -> Intentionally require users to sign in again at cutover or support a short, explicitly time-bounded legacy-token bridge.
- [Cookie authentication introduces CSRF risk] -> Enable Spring CSRF protection, issue an SPA-readable CSRF token, require its header on unsafe methods, and retain SameSite/Secure cookie settings.
- [Per-request session lookup adds database work] -> Use a unique token-hash index, fetch session and user status in one query, throttle activity writes, and measure before adding caching.
- [Permission tightening reveals previously reachable endpoints] -> Add negative integration tests for anonymous, cross-tenant, non-member, and missing-permission requests before changing the default security rule.
- [Dropping organization data is irreversible] -> Confirm that organization data requires no retention, back it up before production migration, and deploy code/schema changes in a controlled release.
- [Project capability payloads can drift from enforcement] -> Generate capabilities from the same `ProjectAccessContext` used by backend guards and test both response and action authorization.
- [Removing legacy endpoints breaks stale clients] -> Migrate the only supported SPA first, communicate the breaking release, and remove compatibility endpoints in the final cleanup step.

## Migration Plan

1. Add session, token-version, and platform RBAC tables and seed an initial platform administrator through controlled runtime configuration or an administrative migration.
2. Add the new Spring Security session filter, standard error handlers, CSRF support, principals, and guards while existing clients still use the old frontend flow.
3. Add bootstrap and project access contracts, migrate project queries/responses, and update frontend state, project navigation, and tenant switching.
4. Move platform endpoints to the platform guard and remove platform permissions from tenant role initialization.
5. Add the forward organization-removal migration and remove organization/data-scope code and UI in the same release.
6. Change the security chain to authenticated-by-default after endpoint classification and negative integration tests pass.
7. Remove legacy authentication, permission, and current-tenant endpoints plus the old token and thread-local implementations; regenerate API clients.
8. Remove expired compatibility data and schedule cleanup of expired/revoked sessions.

Rollback before destructive schema removal can restore the prior application and leave additive tables unused. After organization columns/tables are dropped, rollback requires restoring the database backup; deployment approval must treat that migration as the irreversible boundary.

## Open Questions

None. The platform-only operator boundary, Owner/Admin all-project access, ordinary-member assigned-project access, full organization removal, and browser-session transport have been selected for this change.
