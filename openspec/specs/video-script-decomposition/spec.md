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
The system SHALL allow an authorized tenant or project user to create a decomposition batch containing one or more completed COS upload sessions, where each video represents exactly one episode. A successful creation response SHALL mean that verified objects, the batch, ordered pending episodes, and initial pending attempts are durably stored; it SHALL NOT wait for AI execution creation, billing resolution, point reservation, or provider contact. The application SHALL impose no business file-size ceiling.

#### Scenario: Create a batch from multiple videos
- **WHEN** the user submits an ordered list of completed, verified COS video uploads
- **THEN** the system atomically creates one batch and one pending episode task per video using the submitted order
- **AND** returns the batch identifier, episode numbers, filenames, and initial statuses without synchronously initializing billable AI executions

#### Scenario: Reject an invalid video before task creation
- **WHEN** a submitted upload session is incomplete, belongs to another scope, contains an unsupported video, or exceeds a configured provider duration limit
- **THEN** the system rejects that video with a field-level error
- **AND** creates no batch, episode, or executable AI task for the rejected submission

#### Scenario: Defer an execution initialization failure
- **WHEN** the batch and episodes are valid but a claimed episode later cannot initialize billing or reserve points
- **THEN** the already-created batch remains available
- **AND** the system records the failure on the affected episode without rolling back sibling episodes

### Requirement: Store model-accessible episode video
The system SHALL store each uploaded episode video as an immutable COS object in its authorized tenant or project namespace and SHALL provide the video-understanding worker with an object-scoped COS presigned URL valid for the execution timeout plus 30 minutes.

#### Scenario: Prepare a video for model access
- **WHEN** an episode task begins processing
- **THEN** the worker resolves a valid task-bounded COS URL for the episode video
- **AND** the URL exposes no project API credential, CAM credential, or browser CDN secret

#### Scenario: Fail when the model cannot access the video
- **WHEN** the worker cannot produce or validate a model-accessible URL
- **THEN** the episode task is marked failed with an actionable error
- **AND** no script draft is created for that episode

### Requirement: Parse an episode with Qwen video understanding
The system SHALL asynchronously submit each episode video to the selected enabled video-understanding model and SHALL require a JSON response containing a complete non-empty `script` in the configured Markdown screenplay format.

#### Scenario: Complete direct episode screenplay generation
- **WHEN** the provider returns a valid protocol object and screenplay format
- **THEN** the system stores the raw provider response, normalized protocol JSON, and independent screenplay result
- **AND** the episode status becomes succeeded
- **AND** the AI call log records the provider, model, request ID, duration, and success status

#### Scenario: Reject an invalid screenplay response
- **WHEN** the provider request succeeds but the response is invalid JSON, lacks a non-empty `script`, is truncated, or violates the required screenplay structure
- **THEN** the system retains the raw response for diagnosis
- **AND** marks the episode failed and retryable with `AI_RESPONSE_INVALID`
- **AND** records a business parsing failure against the real AI call

### Requirement: Expose batch and episode progress
The system SHALL expose batch-level counts and percentage plus episode-level execution phase, percentage, status, error, and retryability. For new batches, only an independently saved screenplay result SHALL count as a successful completed episode.

#### Scenario: Query an in-progress batch
- **WHEN** the user requests a batch containing unfinished episode tasks
- **THEN** the response includes total, succeeded, failed, processing, and pending episode counts and a bounded overall percentage
- **AND** each episode includes its current phase, progress percentage, status, error message, and retryability

#### Scenario: Query a completed batch
- **WHEN** every episode has reached a terminal state
- **THEN** the batch is `SUCCEEDED` only when every episode succeeded
- **AND** otherwise the batch distinguishes complete failure from partial failure

#### Scenario: Retry a failed episode
- **WHEN** the user retries a failed retryable episode with sufficient tenant access
- **THEN** the system creates a new technical attempt for only that episode using the same batch model and billing snapshot
- **AND** recalculates progress without resetting successful sibling episodes

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
The system SHALL allow an authenticated tenant member to create a tenant-scoped COS upload session and decomposition batch without providing a project ID. The system SHALL generate the batch identifier and episode numbers from the persisted batch and submitted upload order.

#### Scenario: Upload video without project ID
- **WHEN** a tenant member requests and completes a decomposition video upload session without `projectId`
- **THEN** the upload succeeds under the current tenant's decomposition namespace
- **AND** completion returns the verified storage identity without routing video bytes through Spring

#### Scenario: Create batch without project ID
- **WHEN** a tenant member submits a valid batch name, optional model, and verified uploaded video metadata without `projectId`
- **THEN** the system creates a batch with a system-generated ID and creates episodes numbered according to the metadata order

#### Scenario: Reject a video from another tenant
- **WHEN** batch creation receives an upload session or object key that is not owned by the current tenant
- **THEN** the system rejects the request and creates no batch or episode records

### Requirement: Tenant members can review unbound decomposition batches
The system SHALL allow active tenant members to list and inspect unbound decomposition batches, per-episode screenplay results, progress, and attempts. Historical project-bound batches and their legacy draft or confirmation metadata SHALL remain readable through compatibility responses.

#### Scenario: List unbound batches
- **WHEN** an active tenant member requests decomposition batches without a project filter
- **THEN** the response includes the tenant's unbound batches and their current screenplay-generation progress without requiring project permission

#### Scenario: Inspect an unbound episode
- **WHEN** an active tenant member opens an episode belonging to an unbound batch
- **THEN** the response includes its immutable screenplay result when present, provider evidence, status, progress, and attempts without requiring a project ID

### Requirement: Upload failures explain the failing boundary
The frontend SHALL distinguish browser connectivity failures, upload-session API failures, STS renewal failures, COS multipart failures, and backend completion-verification failures. Each failure SHALL show a clear Chinese message and preserve resumable upload state when safe.

#### Scenario: Browser is offline during upload
- **WHEN** the browser reports that it is offline while a multipart upload is active
- **THEN** the frontend pauses or reports the upload as recoverable and displays an offline connection message

#### Scenario: Temporary credential cannot be renewed
- **WHEN** an upload requests another short-lived COS authorization and the backend signing request fails
- **THEN** the frontend reports a credential-renewal failure without exposing credential values
- **AND** preserves the multipart upload identifier for a safe retry when possible

#### Scenario: COS rejects a multipart operation
- **WHEN** COS returns an error for part upload or completion
- **THEN** the frontend displays the normalized COS upload failure and does not report the material complete

#### Scenario: Backend rejects completion evidence
- **WHEN** `HEAD Object` metadata does not match the authorized upload session
- **THEN** the frontend displays the backend's structured verification error instead of a generic network message

### Requirement: Store independent immutable episode screenplay results
The system SHALL store each new video decomposition screenplay as an independent tenant-scoped result associated with exactly one batch episode. A successful result SHALL NOT create or update a project `Script` or `ScriptVersion`, SHALL NOT require a project binding, and SHALL NOT be editable or replaceable within the same batch.

#### Scenario: Store a successful unbound result
- **WHEN** video understanding for an unbound batch episode returns a valid screenplay
- **THEN** the system stores one immutable screenplay result for that episode
- **AND** marks the episode succeeded without creating project screenplay data

#### Scenario: Reject regeneration of a successful result
- **WHEN** a user requests retry or regeneration for an episode that already has a successful screenplay result
- **THEN** the system rejects the request without issuing another provider call or changing the result
- **AND** the user must create a new batch to obtain a new screenplay

### Requirement: View all batch screenplays by episode
The system SHALL expose a tenant-authorized batch screenplay view that returns every batch episode ordered by `episodeNo`, including the screenplay for succeeded episodes and current progress or failure information for all other episodes. The view SHALL aggregate existing results at read time and SHALL NOT call a model or persist a combined screenplay.

#### Scenario: View a completed batch
- **WHEN** a tenant member opens the all-screenplays view for a completed batch
- **THEN** the system displays every screenplay once in ascending episode order

#### Scenario: View an in-progress batch
- **WHEN** a tenant member opens the all-screenplays view before all episodes finish
- **THEN** succeeded episodes display their screenplay
- **AND** pending, running, and failed episodes display their current status, progress, error, and retry availability instead of a fabricated screenplay

#### Scenario: Copy all available screenplays
- **WHEN** the user copies all screenplays from the batch view
- **THEN** the client concatenates the currently succeeded episode texts in episode order
- **AND** no combined result is written to the server

