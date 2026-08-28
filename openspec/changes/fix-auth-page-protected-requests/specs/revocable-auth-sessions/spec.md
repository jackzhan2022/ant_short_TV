## MODIFIED Requirements

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
