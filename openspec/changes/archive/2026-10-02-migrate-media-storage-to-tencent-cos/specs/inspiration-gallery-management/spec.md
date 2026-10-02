## MODIFIED Requirements

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
