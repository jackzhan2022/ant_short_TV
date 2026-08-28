## Why

The login URL with a trailing slash is misclassified as protected, causing an unnecessary bootstrap request and a 401 response. During an expired-session redirect, AI management pages can also issue a protected Provider request before authentication state has been cleared. The standard JSON error response declares an incompatible character set, obscuring Chinese messages as question marks.

## What Changes

- Normalize authentication-route recognition so canonical and trailing-slash login URLs do not bootstrap an anonymous session.
- Prevent AI Provider and Model pages from loading protected Provider data until their required platform access has been established.
- Make JSON authentication and authorization failure responses explicitly UTF-8 encoded.
- Add frontend and backend regression coverage for these behaviors.

## Capabilities

### New Capabilities
- `authentication-page-request-isolation`: Authentication pages and unauthenticated redirect transitions do not emit protected application API requests.

### Modified Capabilities
- `revocable-auth-sessions`: Standard JSON 401 and 403 responses preserve their declared UTF-8 error messages.

## Impact

- Frontend runtime initialization and AI service management page loading.
- Spring Security authentication entry point and access-denied response handling.
- Existing frontend Vitest and backend MockMvc test suites.
