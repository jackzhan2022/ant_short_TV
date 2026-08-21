## ADDED Requirements

### Requirement: Expose video decomposition as an independent menu
The system SHALL expose video script decomposition through an independent first-level `视频拆剧` menu and SHALL NOT embed its entry point under project detail or project workbench navigation.

#### Scenario: Open the decomposition workspace
- **WHEN** an authorized user selects the `视频拆剧` first-level menu
- **THEN** the system opens the independent decomposition workspace route
- **AND** the workspace can list and create decomposition batches without first opening a project detail page
- **AND** project association is selected or resolved as batch data rather than represented by a nested project route

#### Scenario: Preserve project authorization
- **WHEN** the user lists or operates on a decomposition batch
- **THEN** the system applies the associated project's tenant and permission checks
- **AND** unauthorized batches and episode data remain inaccessible

### Requirement: Create a video script decomposition batch
The system SHALL allow an authorized project user to create a decomposition batch containing one or more uploaded videos, where each video represents exactly one episode.

#### Scenario: Create a batch from multiple videos
- **WHEN** the user submits an ordered list of valid uploaded videos
- **THEN** the system creates one batch and one pending episode task per video using the submitted order
- **AND** the system returns the batch identifier, episode numbers, filenames, and initial statuses

#### Scenario: Reject an invalid video before task creation
- **WHEN** a submitted video is missing, unsupported, too large, or exceeds the configured duration limit
- **THEN** the system rejects that video with a field-level error
- **AND** no executable episode task is created for the rejected video

### Requirement: Store model-accessible episode video
The system SHALL store each uploaded episode video in project-scoped object storage and SHALL provide the video understanding worker with a controlled URL that the Qwen service can access.

#### Scenario: Prepare a video for model access
- **WHEN** an episode task begins processing
- **THEN** the worker resolves a valid model-accessible URL for the episode video
- **AND** the URL does not expose the project's API credentials

#### Scenario: Fail when the model cannot access the video
- **WHEN** the worker cannot produce or validate a model-accessible URL
- **THEN** the episode task is marked failed with an actionable error
- **AND** no script draft is created for that episode

### Requirement: Parse an episode with Qwen video understanding
The system SHALL asynchronously submit each episode video to the configured Alibaba Bailian `qwen3.7-plus` model and SHALL persist a structured episode analysis containing characters, scenes, props, timeline events, dialogue, actions, and emotions when available.

#### Scenario: Complete a structured episode analysis
- **WHEN** Qwen returns a valid structured response
- **THEN** the system stores the normalized JSON analysis and the raw model response
- **AND** the episode analysis status becomes succeeded
- **AND** the AI call log records the provider, model, request ID, duration, and success status

#### Scenario: Reject a non-structured model response
- **WHEN** the provider request succeeds but the response is not valid JSON or misses required analysis fields
- **THEN** the system stores the raw response for diagnosis
- **AND** the episode analysis status becomes failed
- **AND** the AI call log records a business parsing failure rather than a successful decomposition

### Requirement: Generate an episode screenplay draft
The system SHALL generate a screenplay draft for each successfully analyzed episode using the normalized episode analysis and SHALL preserve the episode number in the draft metadata.

#### Scenario: Generate a draft after analysis
- **WHEN** an episode analysis succeeds and the draft generation task is started
- **THEN** the system calls the configured text generation model with the structured analysis
- **AND** stores the generated screenplay draft, episode number, and AI call log reference
- **AND** the draft status becomes pending review

#### Scenario: Retry draft generation for one episode
- **WHEN** the user retries a failed or rejected episode draft
- **THEN** the system reuses the episode video and latest successful analysis
- **AND** creates a new execution attempt without changing other episodes in the batch

### Requirement: Expose batch and episode progress
The system SHALL expose batch-level progress and episode-level statuses for upload, analysis, draft generation, review, confirmation, and failure.

#### Scenario: Query an in-progress batch
- **WHEN** the user requests a batch that has unfinished episode tasks
- **THEN** the response includes total episodes, completed episodes, failed episodes, current phase, and each episode's status and error message

#### Scenario: Retry a failed episode
- **WHEN** the user retries a failed episode with sufficient permission
- **THEN** the system transitions only that episode to a pending state
- **AND** the batch progress is recalculated without resetting successful episodes

### Requirement: Require explicit review before importing a draft
The system SHALL allow users to review and edit an episode screenplay draft and SHALL require explicit confirmation before creating or updating an existing project screenplay version.

#### Scenario: Confirm an episode draft
- **WHEN** the user confirms a reviewed episode draft
- **THEN** the system creates a screenplay version with source type `VIDEO_IMPORT`
- **AND** preserves the episode number and decomposition batch reference
- **AND** marks the episode as confirmed without silently overwriting an unrelated current draft

#### Scenario: Detect a screenplay version conflict
- **WHEN** the current screenplay version changed after the user opened the draft
- **THEN** the confirmation request is rejected with a version conflict
- **AND** the decomposition draft remains available for review

### Requirement: Record real AI execution details
The system SHALL record each video understanding and screenplay generation call in the existing AI call log and SHALL distinguish transport success from business parsing success.

#### Scenario: Record a real Qwen call
- **WHEN** a Qwen request is sent to Alibaba Bailian
- **THEN** the log records the service type, business scene, model, duration, provider request ID, and result status
- **AND** API keys and full authorization headers are excluded from the log

#### Scenario: Handle provider failure
- **WHEN** the Qwen provider returns an error, timeout, or rate-limit response
- **THEN** the episode attempt is marked failed with a retryable or non-retryable classification
- **AND** the provider error is summarized in the AI call log without exposing secrets
