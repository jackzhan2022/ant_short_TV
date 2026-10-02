# generated-image-storage Specification

## Purpose
Persist generated image originals and list-ready thumbnail renditions without storing provider Base64 data in result URL fields.
## Requirements
### Requirement: Generated images are persisted as paired object-storage resources
The system SHALL persist every newly completed generated image as an immutable original in COS and SHALL submit one persistent Cloud Infinite job for an original-resolution `imageSlim` display rendition before publishing a completed result. Sources unsupported by `imageSlim` SHALL be converted to PNG before compression. Thumbnail, detail, and normal-preview roles SHALL share only this display object. The system SHALL retain original dimensions, size, MIME type, object key, ETag or checksum, and rendition processing state.

#### Scenario: Generated image is stored successfully
- **WHEN** an image-generation provider returns a decodable image result
- **THEN** the system stores the original in COS and creates the required persistent `imageSlim` display rendition without resizing
- **AND** completes the result with original and rendition metadata only after required processing succeeds

#### Scenario: Thumbnail storage fails
- **WHEN** the original is stored but Cloud Infinite cannot create or persist the required thumbnail
- **THEN** the system SHALL not publish a completed image result with only an original resource
- **AND** records retryable rendition diagnostics

### Requirement: Image result URLs select the intended rendition
The system SHALL return a permission-checked Type D CDN URL for the compressed display object of every newly completed generated result and SHALL expose the original only through an explicit download action. Image lists, detail views, and normal preview consumers SHALL use the same display object and SHALL NOT render the original as an image source. Signed URLs SHALL not be persisted as resource identities or contain embedded Base64 image data.

#### Scenario: List loads a completed generated result
- **WHEN** an authorized client retrieves a completed generated image task or result list
- **THEN** the response includes an authorized `imageSlim` display URL that does not contain embedded Base64 image data

#### Scenario: Gallery renders a generated result
- **WHEN** the visual gallery renders a completed generated image result
- **THEN** its list tile, detail view, and normal large preview request the same `imageSlim` display object
- **AND** none of those image elements use the original URL as a fallback

#### Scenario: User downloads a generated result
- **WHEN** an authenticated authorized browser explicitly downloads the original image without an `X-Tenant-Id` header
- **THEN** the system derives tenant/project scope from the active result, verifies project view permission, and returns authorized CDN access to the original object

### Requirement: Thumbnail resource is authorized and streamable
The system SHALL preserve the project-scoped thumbnail resource contract while resolving it to the persistent `imageSlim` display object through authorized CDN delivery instead of streaming bytes through the application server. The endpoint SHALL derive tenant scope from an active result belonging to the requested project and SHALL enforce project view permission before creating or returning a delivery grant.

#### Scenario: Authorized thumbnail request without custom tenant header
- **WHEN** an authenticated project member requests the thumbnail for a completed generated result in that project without an `X-Tenant-Id` header
- **THEN** the system returns or redirects to the authorized `imageSlim` display CDN URL

#### Scenario: Unauthorized thumbnail request
- **WHEN** a caller without access to the result project requests its thumbnail
- **THEN** the system rejects the request without exposing an object key or signed CDN URL

#### Scenario: Result does not belong to path project
- **WHEN** an authenticated caller requests an image result under a different project identifier
- **THEN** the system returns a not-found response without accessing the COS object or creating a delivery grant
