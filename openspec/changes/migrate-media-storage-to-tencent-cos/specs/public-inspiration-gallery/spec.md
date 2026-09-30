## MODIFIED Requirements

### Requirement: Imported media is transferred to the platform object bucket
The system SHALL stream each external image or video during import into the configured COS bucket under an immutable platform inspiration asset/version namespace without buffering an entire video in application memory. Imported images SHALL receive one persistent intelligent-compression display WebP shared by thumbnail and preview roles, and imported videos SHALL receive a persistent WebP cover.

#### Scenario: Image creation media is transferred
- **WHEN** an external image creation is imported with a valid media URL
- **THEN** the system streams the original image to an immutable COS original key
- **AND** records that key and the required persistent WebP rendition keys on the public inspiration record

#### Scenario: Video creation media is transferred
- **WHEN** an external video creation is imported with a valid media URL
- **THEN** the system streams the original video to an immutable COS original key
- **AND** records its video MIME type or inferred format and persistent cover state

### Requirement: External media URLs are not persisted or exposed
The system MUST NOT persist external source media URLs, COS URLs, CDN hostnames, or signed query strings as durable public inspiration resource identities. Business data SHALL persist object keys and metadata, and authorized API responses SHALL resolve current CDN delivery URLs from those keys.

#### Scenario: Imported record stores only object identity
- **WHEN** an external creation is imported successfully
- **THEN** the public inspiration record stores its original and rendition object keys and metadata
- **AND** does not store the external media URL or a signed CDN/COS URL

#### Scenario: Detail payload is sanitized
- **WHEN** an external detail response contains media URL fields
- **THEN** the persisted `detail_json` removes those external media URLs or replaces them with internal resource identities that require authorized resolution

#### Scenario: Public API response contains no external media URL
- **WHEN** a client requests the public inspiration list or detail API
- **THEN** the response includes only current authorized platform CDN URLs for imported media

### Requirement: Public inspiration file API streams stored media
The system SHALL preserve `GET /api/inspiration-creations/{id}/file` as the authorization boundary for published, non-deleted inspiration records while returning or redirecting to a Type D CDN URL for the stored original instead of streaming media bytes through the application server.

#### Scenario: Access published media file
- **WHEN** an authenticated client requests the file endpoint for a published, non-deleted inspiration creation
- **THEN** the system verifies the record and caller, resolves the original object key, and returns authorized CDN access

#### Scenario: Unavailable creation file is hidden
- **WHEN** a client requests the file endpoint for an unpublished, deleted, failed, or missing public inspiration creation
- **THEN** the system returns not found and creates no delivery grant

### Requirement: Imported inspiration media has a stored thumbnail
The system SHALL create a persistent thumbnail WebP for each imported inspiration creation and store it under the immutable asset version's derived namespace. Image thumbnails SHALL be generated from the stored original through Cloud Infinite, and video covers SHALL use the deterministic video-cover policy.

#### Scenario: Image import generates a thumbnail
- **WHEN** an external image creation original is imported successfully
- **THEN** Cloud Infinite creates a proportional thumbnail WebP without upscaling
- **AND** the record stores its object key, MIME type, file size, dimensions, and ready status

#### Scenario: Video import generates a thumbnail
- **WHEN** an external video creation original is imported successfully without a bound cover
- **THEN** Cloud Infinite persists a one-second snapshot or bounded first-decodable-frame fallback as WebP
- **AND** the record stores the cover metadata and ready status

#### Scenario: Thumbnail generation fails
- **WHEN** the original media import succeeds but required Cloud Infinite processing fails
- **THEN** the system preserves the imported original media
- **AND** marks rendition generation failed with a clear per-item error
- **AND** allows the idempotent processing job to be retried

### Requirement: Thumbnail files are protected and cacheable
The system SHALL preserve the authenticated thumbnail resource boundary for published, non-deleted records and SHALL return or redirect to a Type D CDN URL for the persistent thumbnail WebP. CDN nodes SHALL cache immutable thumbnails for 30 days and eligible browsers for seven days under the private-media-delivery contract.

#### Scenario: Authenticated user requests a ready published thumbnail
- **WHEN** an authenticated user requests `GET /api/inspiration-creations/{id}/thumbnail` for a published, non-deleted record with a ready thumbnail
- **THEN** the system returns authorized CDN access to the stored thumbnail WebP

#### Scenario: Thumbnail is unavailable
- **WHEN** a user requests the thumbnail endpoint for an unpublished, deleted, missing, failed, or not-ready thumbnail
- **THEN** the system returns not found and exposes no object identity

#### Scenario: Unauthenticated thumbnail request is rejected
- **WHEN** a request without a valid authenticated user queries the thumbnail endpoint
- **THEN** the system rejects the request using the existing authentication behavior

## REMOVED Requirements

### Requirement: Existing inspiration media can be backfilled with thumbnails
**Reason**: The prior backfill reads historical MinIO objects and generates thumbnails with server-side JCodec/ImageIO. Historical MinIO migration is explicitly outside this change, and all new COS media creates persistent Cloud Infinite renditions at ingestion time.

**Migration**: A later historical-media migration must copy each source object into COS and submit the normal persistent rendition workflow instead of restoring this server-side backfill endpoint.
