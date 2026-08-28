# revocable-auth-sessions Specification

## Purpose
TBD - created by archiving change harden-auth-authorization-boundaries. Update Purpose after archive.
## Requirements
### Requirement: Server-side revocable sessions
The system SHALL authenticate browser users with server-side session records backed by random credentials whose stored representation is protected by an environment-provided secret. Production startup MUST fail when required token-protection configuration is absent or invalid.

#### Scenario: Successful login creates a session
- **WHEN** an active user submits valid login credentials
- **THEN** the system creates an active expiring session and delivers its credential in a Secure HttpOnly browser cookie

#### Scenario: Database credentials are not reusable
- **WHEN** an attacker reads an authentication session row
- **THEN** the stored token representation cannot be used directly as the browser session credential

### Requirement: Authentication validates current account state
The system SHALL validate session status, expiry, captured token version, current user status, and user deletion state on every protected request.

#### Scenario: Disabled user is rejected immediately
- **WHEN** an administrator disables a user who still has an unexpired session
- **THEN** the user's next protected request is rejected with 401

#### Scenario: Expired session is rejected
- **WHEN** a request presents a session whose expiry has passed
- **THEN** the request is rejected with 401 and receives no authenticated principal

### Requirement: Session revocation
The system SHALL support revoking the current session independently and invalidating all sessions belonging to a user through token-version rotation.

#### Scenario: Logout revokes only the current session
- **WHEN** a user logs out from one browser session
- **THEN** that session is revoked while another active session for the same user remains valid

#### Scenario: Logout all invalidates prior sessions
- **WHEN** the user's token version is incremented for logout-all, password reset, or administrative invalidation
- **THEN** every session issued with an earlier token version is rejected

### Requirement: Spring Security protects APIs by default
The system MUST place authenticated users in Spring SecurityContext and SHALL require authentication for protected API routes unless an endpoint is explicitly classified as public. Standard JSON authentication and authorization failure responses SHALL declare UTF-8 before writing their error message bodies.

#### Scenario: Anonymous protected request
- **WHEN** an anonymous client requests an API that is not on the explicit public allowlist
- **THEN** the system returns the standard JSON 401 response before controller business logic executes with a UTF-8 declared error message

#### Scenario: Authenticated request obtains a principal
- **WHEN** a valid session requests a protected API
- **THEN** downstream guards and services receive the same authenticated principal from Spring SecurityContext

#### Scenario: Authenticated request lacks required authority
- **WHEN** an authenticated client requests an API without the required authority
- **THEN** the system returns the standard JSON 403 response with a UTF-8 declared error message

### Requirement: Cookie-authenticated writes resist CSRF
The system MUST validate an SPA-compatible CSRF token for unsafe cookie-authenticated requests.

#### Scenario: Missing CSRF token on mutation
- **WHEN** a cookie-authenticated client submits an unsafe method without the required CSRF header
- **THEN** the system rejects the request without executing the mutation

### Requirement: Legacy authentication contracts are retired
The system SHALL remove legacy template authentication endpoints and redundant user/permission endpoints after the SPA uses session bootstrap.

#### Scenario: Legacy endpoint after migration
- **WHEN** a client calls `/api/currentUser`, `/api/login/account`, `/api/login/outLogin`, `/api/user/me`, or `/api/auth/permissions` after migration
- **THEN** the endpoint is not exposed as a supported authentication contract
