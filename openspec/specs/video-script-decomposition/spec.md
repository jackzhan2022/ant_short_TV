# video-script-decomposition Specification

## Purpose
TBD - created by archiving change add-qwen-video-script-decomposition. Update Purpose after archive.
## Requirements
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

### Requirement: Video decomposition can start without a project
The system SHALL allow an authenticated tenant member to upload supported videos and create a decomposition batch without providing a project ID. The system SHALL generate the batch identifier and episode numbers from the persisted batch and submitted upload order.

#### Scenario: Upload video without project ID
- **WHEN** a tenant member uploads a supported video to the video decomposition upload endpoint without `projectId`
- **THEN** the upload succeeds and returns a tenant-level decomposition storage path

#### Scenario: Create batch without project ID
- **WHEN** a tenant member submits a valid batch name, optional model, and uploaded video metadata without `projectId`
- **THEN** the system creates a batch with a system-generated ID and creates episodes numbered according to the metadata order

#### Scenario: Reject a video from another tenant
- **WHEN** batch creation receives a storage path that is not under the current tenant's decomposition namespace
- **THEN** the system rejects the request and creates no batch or episode records

### Requirement: Tenant members can review unbound decomposition batches
The system SHALL allow active tenant members to list and inspect decomposition batches and episodes before a project is selected. Historical project-bound batches SHALL remain readable through the same APIs.

#### Scenario: List unbound batches
- **WHEN** an active tenant member requests decomposition batches without a project filter
- **THEN** the response includes their tenant's unbound batches and their episode progress without requiring a project permission check

#### Scenario: Inspect an unbound episode
- **WHEN** an active tenant member opens an episode belonging to an unbound batch
- **THEN** the response includes the episode analysis, draft, and execution attempts without requiring a project ID

### Requirement: Confirmation binds a draft to a selected project
The system SHALL require a target project ID when confirming a decomposition draft for import. Before creating the `VIDEO_IMPORT` script version, the system SHALL validate access to the target project and bind previously unbound decomposition records to that project.

#### Scenario: Confirm an unbound draft into a project
- **WHEN** a tenant member confirms a valid draft with a target project ID they can use
- **THEN** the system binds the episode and batch to that project and creates a `VIDEO_IMPORT` script version without silently overwriting the current script

#### Scenario: Reject confirmation without project ID
- **WHEN** a tenant member confirms an unbound draft without a target project ID
- **THEN** the system returns a validation error and preserves the draft and unbound state

#### Scenario: Reject confirmation for an inaccessible project
- **WHEN** a tenant member confirms a draft with a project ID they cannot access
- **THEN** the system returns a project access error and creates no imported script version

### Requirement: Upload failures explain the failing boundary
The frontend SHALL distinguish offline state, a request with no server response, and a server response containing a backend error. A no-response upload failure SHALL show a clear Chinese network/service-unavailable message instead of only `Response status:0`, `Network Error`, or `None response! Please retry.`.

#### Scenario: Browser is offline during upload
- **WHEN** the browser reports that it is offline while an upload request fails
- **THEN** the frontend displays an offline connection message

#### Scenario: Backend is unreachable during upload
- **WHEN** an upload request is sent but no HTTP response is received while the browser remains online
- **THEN** the frontend displays a service-unreachable message that tells the user to check whether the backend is running and retry

#### Scenario: Backend returns a structured upload error
- **WHEN** the upload endpoint returns an HTTP error with `errorCode` or `errorMessage`
- **THEN** the frontend displays that backend error detail instead of replacing it with a generic network message

