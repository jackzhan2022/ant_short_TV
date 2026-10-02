# public-inspiration-gallery Specification

## Purpose
TBD - created by archiving change add-public-inspiration-gallery. Update Purpose after archive.
## Requirements
### Requirement: Public inspiration records are stored separately from project materials

The system SHALL persist imported public inspiration creations in a dedicated platform table with stable external identifiers, creation metadata, sanitized detail data, import state, original object storage path, thumbnail metadata and processing state, and local platform URLs for protected original and thumbnail access.

#### Scenario: Imported creation creates a platform record

- **WHEN** the importer processes an external creation list item with a new creation id
- **THEN** the system stores a public inspiration record keyed by that external id
- **AND** the record is not inserted into the tenant/project `material` table

#### Scenario: Duplicate external creation is imported again

- **WHEN** the importer processes an external creation id that already exists
- **THEN** the system updates the existing public inspiration record instead of creating a duplicate row

### Requirement: Imported media is transferred to the platform object bucket
The system SHALL stream each external image or video during import into the configured COS bucket under an immutable platform inspiration asset/version namespace without buffering an entire video in application memory. Imported images SHALL receive one persistent original-resolution `imageSlim` display rendition shared by thumbnail, detail, and preview roles, and imported videos SHALL receive a persistent `imageSlim` cover.

#### Scenario: Image creation media is transferred
- **WHEN** an external image creation is imported with a valid media URL
- **THEN** the system streams the original image to an immutable COS original key
- **AND** records that key and the required persistent display rendition keys on the public inspiration record

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

### Requirement: Inspiration authors are normalized to administrator

The system SHALL display every imported public inspiration creation as authored by `管理员`, regardless of source list or detail author fields.

#### Scenario: Source author is ignored

- **WHEN** an external creation list item includes an author or creator name
- **THEN** the imported public inspiration record stores and returns `管理员` as `authorName`

### Requirement: Import uses external list and detail APIs

The system SHALL import creations by first reading the external list response and then fetching per-creation details for each list item.

#### Scenario: List item triggers detail import

- **WHEN** the external list API returns a creation id
- **THEN** the importer requests the matching external detail endpoint before marking the record imported

#### Scenario: Missing detail prevents public import

- **WHEN** the external detail request fails or returns unusable data for a creation
- **THEN** the system marks that creation import as `FAILED`
- **AND** the creation is not returned by public list or detail APIs

### Requirement: Import failures are isolated per item

The system SHALL continue importing remaining creations when an individual creation fails validation, download, object storage upload, or detail processing.

#### Scenario: One item fails during batch import

- **WHEN** a batch import contains one creation with a missing media URL and another valid creation
- **THEN** the system records the missing-media creation as `FAILED`
- **AND** the system imports the valid creation as `IMPORTED`

#### Scenario: Import error is recorded

- **WHEN** a creation import fails
- **THEN** the system stores a clear import error message for that creation

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
The system SHALL preserve `GET /api/inspiration-creations/{id}/file` as the authorization boundary for published, non-deleted inspiration records while returning or redirecting to a Type D CDN URL for the stored original instead of streaming media bytes through the application server.

#### Scenario: Access published media file
- **WHEN** an authenticated client requests the file endpoint for a published, non-deleted inspiration creation
- **THEN** the system verifies the record and caller, resolves the original object key, and returns authorized CDN access

#### Scenario: Unavailable creation file is hidden
- **WHEN** a client requests the file endpoint for an unpublished, deleted, failed, or missing public inspiration creation
- **THEN** the system returns not found and creates no delivery grant

### Requirement: Imported inspiration media has a stored thumbnail
The system SHALL create a persistent `imageSlim` display object for each imported inspiration creation and store it under the immutable asset version's derived namespace. Image display objects SHALL retain the original dimensions, and video covers SHALL use the deterministic video-cover policy.

#### Scenario: Image import generates a thumbnail
- **WHEN** an external image creation original is imported successfully
- **THEN** Cloud Infinite creates an original-resolution `imageSlim` display object without resizing
- **AND** the record stores its object key, MIME type, file size, dimensions, and ready status

#### Scenario: Video import generates a thumbnail
- **WHEN** an external video creation original is imported successfully without a bound cover
- **THEN** Cloud Infinite persists a one-second snapshot or bounded first-decodable-frame fallback and applies `imageSlim`
- **AND** the record stores the cover metadata and ready status

#### Scenario: Thumbnail generation fails
- **WHEN** the original media import succeeds but required Cloud Infinite processing fails
- **THEN** the system preserves the imported original media
- **AND** marks rendition generation failed with a clear per-item error
- **AND** allows the idempotent processing job to be retried

### Requirement: Thumbnail files are protected and cacheable
The system SHALL preserve the authenticated thumbnail resource boundary for published, non-deleted records and SHALL return or redirect to a Type D CDN URL for the persistent `imageSlim` display object. CDN nodes SHALL cache immutable display objects for 30 days and eligible browsers for seven days under the private-media-delivery contract.

#### Scenario: Authenticated user requests a ready published thumbnail
- **WHEN** an authenticated user requests `GET /api/inspiration-creations/{id}/thumbnail` for a published, non-deleted record with a ready thumbnail
- **THEN** the system returns authorized CDN access to the stored `imageSlim` display object

#### Scenario: Thumbnail is unavailable
- **WHEN** a user requests the thumbnail endpoint for an unpublished, deleted, missing, failed, or not-ready thumbnail
- **THEN** the system returns not found and exposes no object identity

#### Scenario: Unauthenticated thumbnail request is rejected
- **WHEN** a request without a valid authenticated user queries the thumbnail endpoint
- **THEN** the system rejects the request using the existing authentication behavior

### Requirement: Gallery rendering avoids eager original media work

The short-drama creation gallery SHALL render browse cards from thumbnail URLs and SHALL avoid loading additional pages or non-visible preview media before user scroll intent.

#### Scenario: Gallery initially loads

- **WHEN** an authenticated user opens the short-drama creation page without scrolling
- **THEN** the gallery requests and renders only the first page of up to eight inspiration records
- **AND** it does not request original video files for card previews

#### Scenario: Thumbnail approaches the viewport

- **WHEN** a gallery card thumbnail approaches the viewport
- **THEN** the frontend assigns the protected thumbnail URL and allows the browser to decode it asynchronously

#### Scenario: User scrolls to the gallery boundary

- **WHEN** the user has scrolled downward and the next-page boundary enters the observed region
- **THEN** the gallery requests the next page once

#### Scenario: Record has no ready thumbnail

- **WHEN** a gallery list item has no thumbnail URL
- **THEN** the frontend displays a lightweight media-type placeholder
- **AND** does not fetch the original media solely to render the card

