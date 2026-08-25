## ADDED Requirements

### Requirement: User-facing AI operations are durable tasks
The system SHALL represent every user-facing AI operation as a persisted execution task before any provider call and SHALL return a task identifier and current state without waiting for the final AI result.

#### Scenario: Synchronous provider is used behind a task
- **WHEN** a user starts an AI operation backed by a synchronous provider API
- **THEN** the request returns the persisted task and a worker performs the blocking provider call outside the request thread

#### Scenario: Duplicate task creation request
- **WHEN** the same tenant, business operation, and client idempotency key are submitted more than once
- **THEN** the system returns the existing execution task without creating another billable execution

### Requirement: Execution tasks have a canonical lifecycle
The system SHALL persist canonical task states `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELED`, and `TIMED_OUT`, together with current phase, progress, retryability, timestamps, and normalized error details.

#### Scenario: Task reaches a successful terminal state
- **WHEN** all required provider and business-processing phases complete successfully
- **THEN** the execution task becomes `SUCCEEDED`, records completion time and result linkage, and cannot be claimed again for the same execution version

#### Scenario: Business processing fails after provider success
- **WHEN** provider transport succeeds but response parsing, storage, or domain persistence fails
- **THEN** the task and attempt expose a normalized business failure while retaining the successful provider call linkage and usage

### Requirement: Execution tasks retain domain ownership
The system SHALL link each execution task to its tenant, user, project when applicable, stable business scene, domain resource type, domain resource identifier, model selection, trace identifier, and execution version.

#### Scenario: Operator traces a domain result
- **WHEN** an operator opens a generated image, script version, review result, or video result
- **THEN** the system can resolve its execution task, attempts, AI call logs, usage cost, and point settlement without heuristic log searches

### Requirement: Dispatch is durable and recoverable
The system SHALL make committed pending tasks discoverable by a persistent dispatcher and SHALL not depend solely on an in-process callback or thread submission to start execution.

#### Scenario: Process stops after task commit
- **WHEN** the application stops after committing a task but before an in-process worker starts
- **THEN** a later dispatcher scan or durable message redelivery finds and executes the pending task

### Requirement: Provider execution modes are hidden from business callers
The system SHALL support both synchronous provider responses and provider-native asynchronous jobs behind the same execution-task contract.

#### Scenario: Provider returns an external task identifier
- **WHEN** an adapter submits work to a provider-native asynchronous API
- **THEN** the execution stores the external task identifier, schedules status polling, and remains non-terminal until the provider job and business processing finish

#### Scenario: Provider returns the final result immediately
- **WHEN** an adapter uses a synchronous provider API
- **THEN** the worker records the response in the same attempt lifecycle without requiring the domain service to use a different contract

### Requirement: Task controls validate lifecycle state
The system SHALL allow cancellation, retry, and regeneration only from explicitly eligible states and SHALL create a new execution version for intentional regeneration.

#### Scenario: User cancels before provider execution
- **WHEN** a user cancels an unclaimed pending task
- **THEN** the task becomes `CANCELED`, no provider call occurs, and reserved points are released

#### Scenario: User regenerates a successful result
- **WHEN** a user intentionally regenerates from a successful task
- **THEN** the system preserves the prior result and creates a new execution version with new attempt and settlement identities

#### Scenario: Task-scoped domain result is regenerated
- **WHEN** an image or another task-scoped domain result is intentionally regenerated
- **THEN** the system creates a new domain task linked to the source and root execution lineage, assigns the next execution version, and leaves the prior domain task and result unchanged

### Requirement: Clients can observe task progress uniformly
The system SHALL expose a shared task-detail contract containing status, phase, progress, retryability, error details, result references, and settlement summary, regardless of AI business scene.

#### Scenario: Frontend follows a running task
- **WHEN** a client polls or subscribes to an execution task
- **THEN** it receives canonical progress data without parsing domain-specific provider statuses
