## MODIFIED Requirements

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
