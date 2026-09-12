## ADDED Requirements

### Requirement: Generated images are persisted as paired object-storage resources
The system SHALL persist every newly completed generated image as an original object and a proportional PNG thumbnail object in object storage before completing its image result. The thumbnail's longest edge SHALL not exceed 512 pixels, and the system SHALL retain the original dimensions, size, MIME type, and storage path.

#### Scenario: Generated image is stored successfully
- **WHEN** an image-generation provider returns a decodable image result
- **THEN** the system stores both the original and its PNG thumbnail in object storage and completes the result with original metadata

#### Scenario: Thumbnail storage fails
- **WHEN** the original is decoded but its thumbnail cannot be created or stored
- **THEN** the system SHALL not publish a completed image result with only an original resource

### Requirement: Image result URLs select the intended rendition
The system SHALL return a short authenticated original-image URL and thumbnail URL for every newly completed generated result. Image list consumers SHALL use the thumbnail URL, while original preview and download consumers SHALL use the original URL.

#### Scenario: List loads a completed generated result
- **WHEN** an authorized client retrieves a completed generated image task or result list
- **THEN** the response includes a thumbnail URL that does not contain embedded Base64 image data

#### Scenario: User previews or downloads a generated result
- **WHEN** an authorized client requests the original resource for a generated result
- **THEN** the system streams the original object with its detected image media type

### Requirement: Thumbnail resource is authorized and streamable
The system SHALL provide an authenticated project-scoped endpoint that streams the generated result thumbnail as `image/png`.

#### Scenario: Authorized thumbnail request
- **WHEN** a project member requests the thumbnail for a newly completed generated result in that project
- **THEN** the system streams the stored thumbnail as `image/png`

#### Scenario: Unauthorized thumbnail request
- **WHEN** a caller without access to the result project requests its thumbnail
- **THEN** the system rejects the request without exposing the object-storage resource
