## ADDED Requirements

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
