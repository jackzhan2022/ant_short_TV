## MODIFIED Requirements

### Requirement: Atomic AI task claiming
The system SHALL atomically claim every eligible durable AI execution before performing any provider-facing call or poll, regardless of business scene or provider execution mode.

#### Scenario: Single worker claims a pending task
- **WHEN** multiple scheduler workers observe the same pending AI execution at the same time
- **THEN** exactly one worker transitions the execution into the claimed state and performs the provider operation

#### Scenario: Worker loses the claim race
- **WHEN** a worker attempts to claim an execution that another worker already claimed
- **THEN** the losing worker performs no provider operation and records no successful execution attempt

### Requirement: Provider call idempotency
The system SHALL assign a stable idempotency key to each provider-facing phase for an execution task, phase, and execution version, and SHALL persist the key before contacting the provider.

#### Scenario: Duplicate execution attempt for same phase
- **WHEN** the same task phase and execution version is triggered more than once
- **THEN** the system reuses or rejects the duplicate instead of silently issuing an additional provider call

#### Scenario: Regeneration creates a new execution version
- **WHEN** a user intentionally regenerates an AI result
- **THEN** the system creates a new execution version and idempotency key while preserving prior successful results and settlements

### Requirement: Retry eligibility enforcement
The system SHALL validate canonical task and attempt state before allowing a retry or regeneration request and SHALL distinguish infrastructure retry from intentional regeneration.

#### Scenario: Failed task is retried
- **WHEN** a user retries a task in a failed retryable state
- **THEN** the system creates a pending attempt for the eligible phase within the appropriate execution version and leaves successful sibling phases unchanged

#### Scenario: Terminal non-retryable task retry is rejected
- **WHEN** a user attempts to retry a successful, canceled, running, timed-out non-retryable, or otherwise ineligible task
- **THEN** the system rejects the request without changing task state, contacting the provider, or changing settlement

### Requirement: Attempt history is complete
The system SHALL record every accepted provider-facing attempt for every durable AI execution with phase, attempt number, execution version, status, timestamps, idempotency key, provider request id when available, AI call log id when available, usage linkage, and error details when failed.

#### Scenario: Provider transport succeeds but business parsing fails
- **WHEN** the provider returns a transport-level successful response that fails business parsing or normalization
- **THEN** the attempt is marked failed with a business error while the AI call log, usage, cost, and settlement evidence remain linked

#### Scenario: Provider call fails before request id is available
- **WHEN** the provider call fails before a provider request id is returned
- **THEN** the attempt is still recorded with failure status, retryability, error details, and whether provider contact occurred

### Requirement: Observable AI task outcomes
The system SHALL expose persisted information that distinguishes dispatch status, provider transport status, business outcome, task status, retryability, current phase, usage-cost status, and point-settlement status for every AI execution.

#### Scenario: Operator reviews a failed AI task
- **WHEN** an operator opens a failed task detail
- **THEN** the system provides task and attempt state, normalized error, linked call log, provider request id, usage-cost status, point-settlement status, and whether retry is allowed

#### Scenario: Scheduler recovers timed-out execution
- **WHEN** an executing task exceeds its claim timeout without reaching a terminal state
- **THEN** the system expires the claim, records or updates timeout outcome, applies retry policy, and does not leave the task permanently running

## ADDED Requirements

### Requirement: Committed tasks survive dispatch interruption
The system SHALL recover every committed eligible AI task even when the process stops before an in-process callback, executor submission, or non-durable notification runs.

#### Scenario: Dispatch notification is lost
- **WHEN** a task commit succeeds but its immediate dispatch notification is lost
- **THEN** a persistent scheduler or durable message redelivery discovers the task and makes it eligible for atomic claim
