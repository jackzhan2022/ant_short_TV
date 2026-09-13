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

The system SHALL download each external image or video during import and store the media file in the platform object bucket under a platform inspiration path.

#### Scenario: Image creation media is transferred

- **WHEN** an external image creation is imported with a valid media URL
- **THEN** the system uploads the image bytes to object storage under a path such as `inspiration/creations/{externalId}/original.{ext}`
- **AND** the public inspiration record stores that object storage path

#### Scenario: Video creation media is transferred

- **WHEN** an external video creation is imported with a valid media URL
- **THEN** the system uploads the video bytes to object storage under a path such as `inspiration/creations/{externalId}/original.{ext}`
- **AND** the public inspiration record stores a video MIME type or an inferred video format

### Requirement: External media URLs are not persisted or exposed

The system MUST NOT persist external source media URLs in public inspiration business data or return them from public inspiration APIs.

#### Scenario: Imported record stores only local URL

- **WHEN** an external creation is imported successfully
- **THEN** the public inspiration record stores a local URL such as `/api/inspiration-creations/{id}/file`
- **AND** the record does not store the external media URL

#### Scenario: Detail payload is sanitized

- **WHEN** an external detail response contains media URL fields
- **THEN** the persisted `detail_json` removes those external media URLs or replaces them with the local platform URL

#### Scenario: Public API response contains no external media URL

- **WHEN** a client requests the public inspiration list or detail API
- **THEN** the response includes only local platform URLs for imported media

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

The system SHALL expose `GET /api/inspiration-creations` for authenticated users to browse imported public inspiration creations without returning detail JSON.

#### Scenario: Query public inspiration list

- **WHEN** an authenticated user requests `GET /api/inspiration-creations`
- **THEN** the system returns only records with import status `IMPORTED`
- **AND** each item includes browse fields such as id, creation type, task type, title, author name, protected original URL, protected thumbnail URL when ready, MIME type, sort order, and source creation time

#### Scenario: List omits detail JSON

- **WHEN** the public inspiration list API returns items
- **THEN** no item includes `detailJson`

#### Scenario: Unauthenticated list request is rejected

- **WHEN** a request without a valid authenticated user queries `GET /api/inspiration-creations`
- **THEN** the system rejects the request using the existing authentication behavior

### Requirement: Public inspiration detail API returns sanitized detail

The system SHALL expose `GET /api/inspiration-creations/{id}` for authenticated users to load a single imported inspiration creation with sanitized detail data and protected original and thumbnail URLs.

#### Scenario: Query imported creation detail

- **WHEN** an authenticated user requests detail for an imported public inspiration creation
- **THEN** the system returns the list fields plus sanitized `detailJson`
- **AND** includes the local thumbnail URL when thumbnail status is ready

#### Scenario: Failed creation detail is hidden

- **WHEN** an authenticated user requests detail for a failed or missing public inspiration creation
- **THEN** the system returns not found

### Requirement: Public inspiration file API streams stored media

The system SHALL expose `GET /api/inspiration-creations/{id}/file` to stream imported media from the platform object bucket.

#### Scenario: Stream imported media file

- **WHEN** a client requests the file endpoint for an imported public inspiration creation
- **THEN** the system reads the record storage path from object storage
- **AND** streams the file with the stored or inferred content type

#### Scenario: Failed creation file is hidden

- **WHEN** a client requests the file endpoint for a failed or missing public inspiration creation
- **THEN** the system returns not found

### Requirement: Imported inspiration media has a stored thumbnail

The system SHALL generate a bounded visual thumbnail for each imported inspiration creation and store it as a separate object under the platform inspiration path.

#### Scenario: Image import generates a thumbnail

- **WHEN** an external image creation is imported successfully
- **THEN** the system generates a reduced-size thumbnail without upscaling the original
- **AND** uploads the thumbnail under a path such as `inspiration/creations/{externalId}/thumbnail.jpg`
- **AND** stores the thumbnail path, local URL, MIME type, file size, and ready status on the inspiration record

#### Scenario: Video import generates a thumbnail

- **WHEN** an external video creation is imported successfully
- **THEN** the system extracts a representative frame using the configured video frame extractor
- **AND** uploads a reduced-size thumbnail under the platform inspiration path
- **AND** stores the thumbnail metadata and ready status on the inspiration record

#### Scenario: Thumbnail generation fails

- **WHEN** the original media import succeeds but thumbnail decoding, extraction, encoding, or upload fails
- **THEN** the system preserves the imported original media
- **AND** marks thumbnail generation as failed with a clear per-item error
- **AND** allows the thumbnail operation to be retried

### Requirement: Existing inspiration media can be backfilled with thumbnails

The system SHALL provide a resumable bounded operation that generates thumbnails for imported inspiration records that do not have a ready thumbnail, using the original objects already stored by the platform.

#### Scenario: Backfill processes a historical record

- **WHEN** backfill selects an imported record without a ready thumbnail
- **THEN** the system reads the original object from platform storage
- **AND** generates and uploads the deterministic thumbnail object
- **AND** updates that record to thumbnail status ready

#### Scenario: Backfill is rerun

- **WHEN** backfill runs again after some records already have ready thumbnails
- **THEN** the system skips ready records
- **AND** continues processing eligible pending or failed records within the configured batch bound

#### Scenario: One historical item fails

- **WHEN** thumbnail generation fails for one record in a backfill batch
- **THEN** the system records that item failure
- **AND** continues processing the remaining records in the batch

### Requirement: Thumbnail files are protected and cacheable

The system SHALL expose stored thumbnails through an authenticated local file endpoint and SHALL apply finite private browser caching to stable original and thumbnail responses.

#### Scenario: Authenticated user requests a ready thumbnail

- **WHEN** an authenticated user requests `GET /api/inspiration-creations/{id}/thumbnail` for an imported record with a ready thumbnail
- **THEN** the system streams the thumbnail from platform object storage with its stored MIME type
- **AND** returns a finite private cache policy

#### Scenario: Thumbnail is missing or not ready

- **WHEN** a user requests the thumbnail endpoint for a missing, failed, or not-ready thumbnail
- **THEN** the system returns not found

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

