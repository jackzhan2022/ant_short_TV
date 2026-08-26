## ADDED Requirements

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
