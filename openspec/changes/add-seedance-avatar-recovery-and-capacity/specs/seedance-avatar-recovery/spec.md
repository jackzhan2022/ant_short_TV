## ADDED Requirements

### Requirement: Automatically recover explicit real-person image rejections once
The system SHALL create at most one durable recovery workflow for an original native Seedance video task explicitly rejected for an input image containing a real person. A recovered child task MUST NOT trigger another automatic recovery.

#### Scenario: Synchronous or asynchronous image rejection
- **WHEN** submission returns a definitive rejection or polling returns a failed task with explicit real-person image semantics
- **THEN** the original failure and settlement remain recorded and one recovery workflow starts

#### Scenario: Ambiguous transport or unrelated moderation failure
- **WHEN** a timeout, unknown submission, authentication failure, rate limit or unrelated moderation rejection occurs
- **THEN** no automatic asset ingestion is started

### Requirement: Ingest frozen image references before retrying
The system SHALL ingest or reuse only provider-identified or portrait-eligible image references from the failed request snapshot and SHALL wait until every required image is Active before creating the linked retry. Prompt, model, parameters, reference ordering, labels and unselected image and non-image references MUST remain unchanged.

#### Scenario: Partial readiness and later source edits
- **WHEN** some references are Processing and the user replaces the currently displayed character image
- **THEN** no video retry is submitted yet and recovery continues to use the original frozen image sources

#### Scenario: A prepared snapshot is retried
- **WHEN** all snapshot image references are Active
- **THEN** one linked video task is created using the corresponding asset:// identities and normal point reservation

### Requirement: Durable and visible recovery progress
The system SHALL persist recovery stages, source and child task associations, failures and processing claims, and SHALL expose scoped progress on the current storyboard. Browser closure MUST NOT cause duplicate ingestion or duplicate video creation.

#### Scenario: Refresh or multiple clients
- **WHEN** multiple clients view or retry the same failed task, or refresh during ingestion
- **THEN** they observe the same workflow and at most one linked retry task exists

#### Scenario: Unrecoverable input or insufficient balance
- **WHEN** no eligible frozen source is available, ingestion fails, or the retry cannot reserve points
- **THEN** the recovery reports the specific failure without claiming a retry was submitted

### Requirement: Target portrait references without consuming non-portrait slots
The system SHALL prefer provider-reported content positions when available, otherwise select character and unclassified image references. Explicit scene and prop images SHALL retain their original URLs unless the provider specifically identifies them as rejected. Existing virtual identities SHALL be verified before reuse.

#### Scenario: A character and scene are referenced together
- **WHEN** a real-person rejection occurs without a content index
- **THEN** the character image is prepared while scene, prop and audio/video references remain in their original order and retain their URLs

#### Scenario: A specific image-material reference is rejected
- **WHEN** the provider identifies content[2] as the rejected input image
- **THEN** that image is prepared without ingesting other unconverted images unnecessarily
