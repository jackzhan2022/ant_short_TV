# inspiration-gallery-management Specification

## Purpose
TBD - created by archiving change add-inspiration-square-management. Update Purpose after archive.
## Requirements
### Requirement: Only platform administrators can manage inspiration content
The system MUST expose inspiration management capabilities only to users holding the `PLATFORM_INSPIRATION_MANAGE` permission, and the frontend SHALL show the management entry only to platform administrators.

#### Scenario: Administrator opens management drawer
- **WHEN** a platform administrator opens the short-drama creation page and selects the inspiration management entry
- **THEN** the frontend opens the inspiration management drawer
- **AND** the administrator can query managed inspiration records

#### Scenario: Unauthorized user calls management API
- **WHEN** an authenticated user without `PLATFORM_INSPIRATION_MANAGE` calls an inspiration management endpoint
- **THEN** the system rejects the request as forbidden

### Requirement: Administrator can upload image inspiration content
The system SHALL allow an administrator to create inspiration content by uploading a supported image directly to a scoped COS upload session together with a title, zero or more category tags, a complete prompt, an initial publication status, and a sort position. The application SHALL impose no business file-size ceiling, SHALL preserve the original, and SHALL require one persistent original-resolution Cloud Infinite `imageSlim` display rendition before publishing the record. Thumbnail, detail, and preview roles SHALL share only that object.

#### Scenario: Valid image is uploaded
- **WHEN** an administrator selects a supported image and completes its assigned COS upload session
- **THEN** the backend verifies the object and decoded media metadata without proxying its bytes through Spring
- **AND** Cloud Infinite persists the configured `imageSlim` display rendition
- **AND** the management record is created only after required media operations succeed

#### Scenario: Image cannot be decoded or processed
- **WHEN** the uploaded object is not a supported decodable image or its required conversion or `imageSlim` processing fails
- **THEN** the system prevents publication, preserves entered metadata where possible, and reports an actionable media-processing error

### Requirement: Administrator can upload video inspiration content
The system SHALL allow an administrator to create inspiration content by uploading a supported video directly to a scoped COS upload session together with its metadata and SHALL create a persistent `imageSlim` cover through Cloud Infinite. The application SHALL impose no business file-size ceiling.

#### Scenario: Valid video is uploaded
- **WHEN** an administrator completes a supported video upload session
- **THEN** the backend verifies and records the original COS object
- **AND** Cloud Infinite persists a bounded deterministic cover from the one-second snapshot or first decodable-frame fallback
- **AND** the inspiration record is created only after both required media operations succeed

#### Scenario: Video thumbnail generation fails
- **WHEN** the video is stored but its required Cloud Infinite cover cannot be generated
- **THEN** the system does not create a managed inspiration record
- **AND** records or removes objects created by the failed workflow according to the temporary-object lifecycle
- **AND** returns an actionable retryable processing failure

### Requirement: Managed content includes prompt and presentation metadata
The system SHALL persist each managed inspiration record with a title, category tags, complete prompt text, media type, source type, publication status, and sort order.

#### Scenario: Administrator edits metadata
- **WHEN** an administrator updates the title, tags, or complete prompt of an existing record
- **THEN** the system persists the new metadata without replacing the stored media
- **AND** subsequent management and public detail responses reflect the update

#### Scenario: Gallery card renders prompt summary
- **WHEN** a published record with a complete prompt appears in the public gallery
- **THEN** its card may render a bounded prompt summary
- **AND** opening the detail shows the complete prompt with a copy action

### Requirement: Administrator controls publication state
The system SHALL let an administrator publish or unpublish any non-deleted inspiration record.

#### Scenario: Administrator publishes content
- **WHEN** an administrator changes a draft or unpublished record to `PUBLISHED`
- **THEN** the record becomes eligible for the public gallery immediately

#### Scenario: Administrator unpublishes content
- **WHEN** an administrator changes a published record to `UNPUBLISHED`
- **THEN** the record remains visible in the management list
- **AND** it is no longer available through public list, detail, original media, or thumbnail endpoints

### Requirement: Administrator can search and filter managed content
The system SHALL provide a paginated management list that can search by title or prompt and filter by publication status and media type.

#### Scenario: Administrator filters managed content
- **WHEN** an administrator supplies a keyword, publication status, or media type filter
- **THEN** the management API returns only non-deleted records matching all supplied filters
- **AND** returns pagination totals for that filtered result

### Requirement: Administrator can reorder inspiration content
The system SHALL allow an administrator to submit the ordered identifiers of managed inspiration records and SHALL update their sort order atomically.

#### Scenario: Drag order is saved
- **WHEN** an administrator drags records into a new order and saves that order
- **THEN** the system validates the submitted identifiers
- **AND** updates all affected sort values in one transaction
- **AND** public gallery ordering reflects the new sequence

#### Scenario: Invalid order is submitted
- **WHEN** the submitted order contains an unknown, deleted, or duplicate identifier
- **THEN** the system rejects the entire reorder request
- **AND** preserves the previous ordering

### Requirement: Administrator can delete inspiration content
The system SHALL allow an administrator to delete a managed inspiration record so that it is immediately excluded from management and public reads.

#### Scenario: Administrator confirms deletion
- **WHEN** an administrator confirms deletion of an inspiration record
- **THEN** the system marks the record deleted
- **AND** immediately excludes it from all list, detail, original media, and thumbnail queries
- **AND** attempts to remove its stored media objects after the database change succeeds
