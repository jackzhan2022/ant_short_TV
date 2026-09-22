## ADDED Requirements

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
The system SHALL allow an administrator to create inspiration content by uploading an image together with a title, zero or more category tags, a complete prompt, an initial publication status, and a sort position.

#### Scenario: Valid image is uploaded
- **WHEN** an administrator selects a supported image
- **THEN** the frontend compresses it proportionally to a longest edge of at most 1920 pixels and a target size of at most 1.5MB
- **AND** the frontend shows the original and compressed file sizes
- **AND** the backend validates the decoded image constraints before storing the compressed image as the original gallery media

#### Scenario: Image cannot meet upload constraints
- **WHEN** the browser cannot decode or compress the image within the required dimension and size limits
- **THEN** the frontend prevents submission
- **AND** it preserves the entered metadata and prompts the administrator to choose another file

### Requirement: Administrator can upload video inspiration content
The system SHALL allow an administrator to create inspiration content by uploading a supported video together with its metadata and SHALL generate a stored thumbnail from that video.

#### Scenario: Valid video is uploaded
- **WHEN** an administrator submits a supported video within the configured upload limit
- **THEN** the system stores the original video in platform object storage
- **AND** extracts and stores a bounded representative-frame thumbnail
- **AND** creates the inspiration record only after both media operations succeed

#### Scenario: Video thumbnail generation fails
- **WHEN** the video is stored but its thumbnail cannot be generated
- **THEN** the system does not create a managed inspiration record
- **AND** removes objects created by the failed request where possible
- **AND** returns an actionable upload failure

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

