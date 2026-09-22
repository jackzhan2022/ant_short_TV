## MODIFIED Requirements

### Requirement: Public inspiration list API is lightweight

The system SHALL expose `GET /api/inspiration-creations` for authenticated users to browse only published, non-deleted public inspiration creations without returning detail JSON.

#### Scenario: Query public inspiration list

- **WHEN** an authenticated user requests `GET /api/inspiration-creations`
- **THEN** the system returns only records with import status `IMPORTED`, publication status `PUBLISHED`, and no deletion marker
- **AND** each item includes browse fields such as id, creation type, task type, title, author name, protected original URL, protected thumbnail URL when ready, MIME type, tags, prompt summary, sort order, and source creation time

#### Scenario: List omits detail JSON and complete prompt

- **WHEN** the public inspiration list API returns items
- **THEN** no item includes `detailJson` or the complete prompt text

#### Scenario: Unauthenticated list request is rejected

- **WHEN** a request without a valid authenticated user queries `GET /api/inspiration-creations`
- **THEN** the system rejects the request using the existing authentication behavior

### Requirement: Public inspiration detail API returns sanitized detail

The system SHALL expose `GET /api/inspiration-creations/{id}` for authenticated users to load a single published, non-deleted inspiration creation with sanitized detail data, complete prompt text, tags, and protected original and thumbnail URLs.

#### Scenario: Query published creation detail

- **WHEN** an authenticated user requests detail for a published public inspiration creation
- **THEN** the system returns the list fields plus sanitized `detailJson`, complete prompt text, and tags
- **AND** includes the local thumbnail URL when thumbnail status is ready

#### Scenario: Unpublished, deleted, or failed creation detail is hidden

- **WHEN** an authenticated user requests detail for an unpublished, deleted, failed, or missing public inspiration creation
- **THEN** the system returns not found

### Requirement: Public inspiration file API streams stored media

The system SHALL expose `GET /api/inspiration-creations/{id}/file` to stream stored media for published, non-deleted inspiration records from the platform object bucket.

#### Scenario: Stream published media file

- **WHEN** a client requests the file endpoint for a published, non-deleted inspiration creation
- **THEN** the system reads the record storage path from object storage
- **AND** streams the file with the stored or inferred content type

#### Scenario: Unavailable creation file is hidden

- **WHEN** a client requests the file endpoint for an unpublished, deleted, failed, or missing public inspiration creation
- **THEN** the system returns not found

### Requirement: Thumbnail files are protected and cacheable

The system SHALL expose stored thumbnails for published, non-deleted records through an authenticated local file endpoint and SHALL apply finite private browser caching to stable original and thumbnail responses.

#### Scenario: Authenticated user requests a ready published thumbnail

- **WHEN** an authenticated user requests `GET /api/inspiration-creations/{id}/thumbnail` for a published, non-deleted record with a ready thumbnail
- **THEN** the system streams the thumbnail from platform object storage with its stored MIME type
- **AND** returns a finite private cache policy

#### Scenario: Thumbnail is unavailable

- **WHEN** a user requests the thumbnail endpoint for an unpublished, deleted, missing, failed, or not-ready thumbnail
- **THEN** the system returns not found

#### Scenario: Unauthenticated thumbnail request is rejected

- **WHEN** a request without a valid authenticated user queries the thumbnail endpoint
- **THEN** the system rejects the request using the existing authentication behavior
