## ADDED Requirements

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

## MODIFIED Requirements

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

### Requirement: Tenant members can review unbound decomposition batches
The system SHALL allow active tenant members to list and inspect unbound decomposition batches, per-episode screenplay results, progress, and attempts. Historical project-bound batches and their legacy draft or confirmation metadata SHALL remain readable through compatibility responses.

#### Scenario: List unbound batches
- **WHEN** an active tenant member requests decomposition batches without a project filter
- **THEN** the response includes the tenant's unbound batches and their current screenplay-generation progress without requiring project permission

#### Scenario: Inspect an unbound episode
- **WHEN** an active tenant member opens an episode belonging to an unbound batch
- **THEN** the response includes its immutable screenplay result when present, provider evidence, status, progress, and attempts without requiring a project ID

## REMOVED Requirements

### Requirement: Generate an episode screenplay draft
**Reason**: New episodes receive the complete screenplay directly from the video-understanding call, so a separate text draft-generation stage is misleading and creates inconsistent model behavior.

**Migration**: Preserve execution support for historical episodes already in `PENDING_DRAFT` or `DRAFT_GENERATING`; do not create that phase for new batches.

### Requirement: Require explicit review before importing a draft
**Reason**: Video decomposition now ends with an immutable independent screenplay result and no longer edits or imports project screenplay versions.

**Migration**: Keep historical draft, confirmation, and imported version data readable; remove review and confirmation actions for new results.

### Requirement: Confirmation binds a draft to a selected project
**Reason**: New decomposition results remain tenant-level and independent from project screenplay creation.

**Migration**: Preserve existing project bindings and imported versions for historical batches without exposing confirmation for new results.
