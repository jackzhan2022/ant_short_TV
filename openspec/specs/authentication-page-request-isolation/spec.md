# authentication-page-request-isolation Specification

## Purpose
Ensure public authentication routes do not initiate protected application requests while redirects and stale application views are resolving.

## Requirements
### Requirement: Authentication pages isolate protected application requests
The frontend SHALL treat configured authentication routes with or without one trailing slash as public routes and SHALL not call authenticated bootstrap on those routes. AI Provider and Model pages SHALL not load Provider data until their corresponding platform view capability is established.

#### Scenario: Trailing-slash login route
- **WHEN** an anonymous browser opens `/user/login/`
- **THEN** the frontend renders the authentication page without calling `/api/auth/bootstrap`

#### Scenario: Unauthenticated redirect retains a stale Model page briefly
- **WHEN** the Model page is rendered without the platform Model-view capability during a redirect to login
- **THEN** it does not call `/api/platform/ai/providers`

#### Scenario: Unauthenticated redirect retains a stale Provider page briefly
- **WHEN** the Provider page is rendered without the platform Provider-view capability during a redirect to login
- **THEN** it does not call `/api/platform/ai/providers`
