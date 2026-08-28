## Context

The SPA uses authenticated bootstrap data to establish user and permission state. Its public-route comparison currently requires an exact pathname, while browsers and reverse proxies can preserve a trailing slash. AI Provider data is loaded by mounted Provider and Model pages without first confirming the corresponding platform access. Spring Security writes JSON authentication failures through custom handlers.

## Goals / Non-Goals

**Goals:**
- Keep all authentication URL variants outside authenticated bootstrap.
- Prevent protected Provider API calls while a page has no confirmed platform access.
- Preserve Chinese JSON error messages through explicit UTF-8 response encoding.

**Non-Goals:**
- Change backend authentication or authorization policy.
- Make Provider APIs public, retry failed anonymous requests, or alter platform permissions.
- Redesign AI management navigation.

## Decisions

### Normalize only authentication route matching

Use a shared authentication-route predicate that accepts exactly the configured authentication paths with an optional terminal slash. This is narrower than normalizing every application pathname and keeps existing route semantics unchanged.

Alternative: redirect the trailing-slash URL at the reverse proxy. This does not protect SPA transitions and leaves the client-side route classification defect in place.

### Gate page-owned Provider loads by existing access capabilities

Provider and Model pages will avoid starting their Provider requests unless their relevant platform view capability is true. The Models page needs a confirmed Model-view capability; the Providers page needs Provider-view capability. An unauthenticated redirect therefore produces no protected request even if a stale page renders briefly.

Alternative: add a global request interceptor that cancels protected requests on authentication routes. That would conceal unexpected mounted components and complicate normal request error behavior, rather than fixing the page data-load boundary.

### Set UTF-8 before writing custom security JSON

Both custom Spring Security handlers will explicitly set UTF-8 before `ObjectMapper` obtains the response writer. This preserves the existing status codes and error response shape while making the declared and actual encoding unambiguous.

Alternative: configure a global servlet encoding default only. Explicit handler configuration covers custom responses regardless of deployment defaults.

## Risks / Trade-offs

- [A page is mounted without the expected capability because of a separate routing defect] → It displays no protected data and sends no protected API request; route behavior remains covered by existing navigation tests.
- [A platform role can view Models but cannot fetch Providers] → Existing backend permission behavior remains authoritative; tests use the current access contract and do not expand authorization.
- [A proxy rewrites path casing or segments] → Only an optional trailing slash is accepted, avoiding unexpected public-route expansion.

## Migration Plan

1. Deploy frontend and backend changes together or independently; each change is backward-compatible.
2. Verify anonymous access to both canonical and trailing-slash login URLs produces no protected API call.
3. Verify a protected API still returns JSON 401/403 with UTF-8 text when called anonymously or without authority.
4. Roll back either artifact independently if needed; no schema or session migration is involved.

## Open Questions

None.
