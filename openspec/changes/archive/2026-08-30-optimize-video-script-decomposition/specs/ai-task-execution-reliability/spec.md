## MODIFIED Requirements

### Requirement: Provider call idempotency
The system SHALL assign a stable idempotency key to each provider-facing AI execution phase for a task and execution version. A technical retry of a failed video decomposition episode SHALL preserve the existing execution version and frozen model and billing configuration, while a user-requested new result SHALL be represented by a new batch and task.

#### Scenario: Duplicate execution attempt for same phase
- **WHEN** the same task phase and version is triggered more than once without an accepted retry transition
- **THEN** the system reuses or rejects the duplicate instead of silently issuing an additional provider call

#### Scenario: Technical retry preserves the execution version
- **WHEN** a failed retryable video decomposition episode is retried
- **THEN** the new attempt uses the same execution version, selected model, configuration snapshot, and billing versions

#### Scenario: User requests a new screenplay after success
- **WHEN** a user wants a different result for an episode whose screenplay succeeded
- **THEN** the existing task rejects regeneration
- **AND** a newly created batch receives a new task, execution version, and currently effective configuration

### Requirement: Retry eligibility enforcement
The system SHALL validate task state before accepting a retry. Only a failed retryable video decomposition episode SHALL be retryable in its existing batch; pending, running, succeeded, and historical confirmed tasks SHALL reject retry or regeneration.

#### Scenario: Failed task is retried
- **WHEN** a user retries a task in a failed retryable state
- **THEN** the system creates a new pending attempt for that task and leaves successful sibling tasks unchanged

#### Scenario: Successful task retry is rejected
- **WHEN** a user attempts to retry or regenerate a succeeded, confirmed, running, pending, or otherwise non-retryable task
- **THEN** the system rejects the request without changing task status or issuing a provider call

### Requirement: Direct video screenplay uses one billable execution stage
For each new video decomposition episode, the system SHALL create, settle, and expose one video-understanding execution stage that directly returns the screenplay. It SHALL not create a text draft-generation execution, reservation, usage record, or provider call for that episode.

#### Scenario: Direct screenplay analysis succeeds
- **WHEN** a new video decomposition episode completes video understanding with a valid screenplay
- **THEN** the video-understanding execution is settled as successful and linked to the immutable screenplay result
- **AND** no `DRAFT_GENERATION` attempt or `video_script_draft` call log exists for that episode

#### Scenario: Historical draft-generation task runs after deployment
- **WHEN** an episode already in `PENDING_DRAFT` or `DRAFT_GENERATING` is processed after deployment
- **THEN** its legacy draft-generation execution and settlement behavior remain available

## ADDED Requirements

### Requirement: Batch progress derives from persisted episode executions
The system SHALL calculate video decomposition batch counts and percentage from persisted episode and execution states, and SHALL not treat historical review or confirmation state as a required phase for new batches.

#### Scenario: Calculate running batch progress
- **WHEN** a batch contains pending, running, succeeded, and failed episodes
- **THEN** the batch response reports mutually consistent counts whose sum equals the total episode count
- **AND** reports an overall percentage between 0 and 100 derived from the episode execution percentages

#### Scenario: Complete a new batch
- **WHEN** all new-batch episodes have succeeded
- **THEN** the batch becomes `SUCCEEDED` with 100 percent progress without waiting for review or confirmation
