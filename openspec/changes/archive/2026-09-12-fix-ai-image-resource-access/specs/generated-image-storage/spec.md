## MODIFIED Requirements

### Requirement: Image result URLs select the intended rendition
The system SHALL return a short authenticated original-image URL and thumbnail URL for every newly completed generated result. Browser-native requests to these URLs SHALL authorize from the authenticated session and persisted result ownership without requiring a custom tenant header. Image list consumers SHALL use the thumbnail URL, while original preview and download consumers SHALL use the original URL.

#### Scenario: List loads a completed generated result
- **WHEN** an authorized client retrieves a completed generated image task or result list
- **THEN** the response includes a thumbnail URL that does not contain embedded Base64 image data

#### Scenario: Gallery renders a generated result
- **WHEN** the visual gallery renders a completed generated image result
- **THEN** its list tile requests the thumbnail URL and its large preview requests the original-image URL

#### Scenario: User previews or downloads a generated result
- **WHEN** an authenticated authorized browser requests the original resource without an `X-Tenant-Id` header
- **THEN** the system derives the authorization scope from the active result and streams the original object with its detected image media type

### Requirement: Thumbnail resource is authorized and streamable
The system SHALL provide an authenticated project-scoped endpoint that streams the generated result thumbnail as `image/png`. The endpoint SHALL derive tenant scope from an active result belonging to the requested project and SHALL enforce project view permission before accessing object storage.

#### Scenario: Authorized thumbnail request without custom tenant header
- **WHEN** an authenticated project member requests the thumbnail for a completed generated result in that project without an `X-Tenant-Id` header
- **THEN** the system streams the stored thumbnail as `image/png`

#### Scenario: Unauthorized thumbnail request
- **WHEN** an authenticated caller without access to the result project requests its thumbnail
- **THEN** the system returns a forbidden response without exposing or reading the object-storage resource

#### Scenario: Result does not belong to path project
- **WHEN** an authenticated caller requests an image result under a different project identifier
- **THEN** the system returns a not-found response without exposing or reading the object-storage resource
