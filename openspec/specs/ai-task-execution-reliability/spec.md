# ai-task-execution-reliability Specification

## Purpose
TBD - created by archiving change stabilize-ai-task-execution. Update Purpose after archive.
## Requirements
### Requirement: Atomic AI task claiming

The system SHALL atomically claim an eligible asynchronous AI task before performing any external provider call.

#### Scenario: Single worker claims a pending task

- **WHEN** multiple scheduler workers observe the same pending AI task at the same time
- **THEN** exactly one worker transitions the task into an executing state and performs the provider call

#### Scenario: Worker loses the claim race

- **WHEN** a worker attempts to claim a task that another worker already claimed
- **THEN** the losing worker does not perform any external provider call and records no successful execution attempt

### Requirement: Provider call idempotency

The system SHALL assign a stable idempotency key to each provider-facing AI execution phase for a task, phase, and version.

#### Scenario: Duplicate execution attempt for same phase

- **WHEN** the same task phase and version is triggered more than once
- **THEN** the system reuses or rejects the duplicate execution instead of silently issuing an additional provider call

#### Scenario: Regeneration creates a new execution version

- **WHEN** a user intentionally regenerates an AI result
- **THEN** the system creates a new execution version and idempotency key while preserving prior successful results

### Requirement: Retry eligibility enforcement

The system SHALL validate task state before allowing a retry or regeneration request.

#### Scenario: Failed task is retried

- **WHEN** a user retries a task in a failed retryable state
- **THEN** the system creates a new pending attempt for the requested phase and leaves successful sibling tasks unchanged

#### Scenario: Confirmed task retry is rejected

- **WHEN** a user attempts to retry a confirmed, successful, running, or otherwise non-retryable task
- **THEN** the system rejects the request without changing task status or issuing a provider call

### Requirement: Attempt history is complete

The system SHALL record every accepted asynchronous AI execution attempt with phase, attempt number, status, timestamps, provider request id when available, AI call log id when available, and error details when failed.

#### Scenario: Provider transport succeeds but business parsing fails

- **WHEN** the provider returns a transport-level successful response that fails business parsing or normalization
- **THEN** the attempt is marked failed with a business error and the AI call log remains linked for diagnostics

#### Scenario: Provider call fails before request id is available

- **WHEN** the provider call fails before a provider request id is returned
- **THEN** the attempt is still recorded with failure status, retryability, and error details

### Requirement: Observable AI task outcomes

The system SHALL expose enough persisted information to distinguish provider transport status, business outcome, task status, retryability, and current execution phase.

#### Scenario: Operator reviews a failed AI task

- **WHEN** an operator or authorized user opens a failed task detail
- **THEN** the system provides the latest task status, latest attempt status, error code, error message, linked AI call log id, and whether retry is allowed

#### Scenario: Scheduler recovers timed-out execution

- **WHEN** an executing task exceeds its configured timeout without reaching a terminal state
- **THEN** the system marks the execution as timed out or retryable according to the workflow rules and does not leave the task permanently running

### Requirement: AI tasks require complete effective model billing
Before an AI task can reserve points or contact a provider, the system SHALL resolve effective supplier cost prices and user point prices for the selected model, required usage metrics, dimensions, and task creation time. Both rule sets SHALL cover every required metric.

#### Scenario: Both model price rule sets cover the task
- **WHEN** a task is created for a model with effective cost and point components covering all required metrics
- **THEN** the system SHALL create the execution using the resolved price versions and may proceed with point reservation and provider dispatch

#### Scenario: Cost or point price is missing
- **WHEN** a task is created for a model without an effective cost price or point price for any required metric
- **THEN** the system SHALL reject the task before provider contact and SHALL not create a point reservation, usage line, cost line, or provider call log

### Requirement: AI execution billing snapshots remain stable
The system SHALL persist the resolved model cost-price version and point-price version with an execution version and SHALL use those frozen versions for all retries, usage costing, and point settlement belonging to that execution version.

#### Scenario: Infrastructure retry uses original billing versions
- **WHEN** a retry is initiated for an existing execution version after an administrator publishes a new price version
- **THEN** the retry SHALL use the price versions frozen by the existing execution version and SHALL not create an additional point reservation for the same idempotency key

#### Scenario: Regeneration uses current effective billing versions
- **WHEN** a user intentionally regenerates an AI result after a new price version becomes effective
- **THEN** the system SHALL create a new execution version and resolve the billing versions effective at that new execution's creation time

### Requirement: Accounting details show dual-price evidence
The system SHALL make execution accounting details available to authorized platform users with immutable usage, supplier cost, user point settlement, and the cost-price and point-price versions used for the execution.

#### Scenario: Platform administrator reviews a settled execution
- **WHEN** an authorized platform administrator opens a settled execution's accounting detail
- **THEN** the system SHALL return the usage metrics, cost currency and amount, settled points, corresponding price version identifiers, component identifiers, and settlement status

