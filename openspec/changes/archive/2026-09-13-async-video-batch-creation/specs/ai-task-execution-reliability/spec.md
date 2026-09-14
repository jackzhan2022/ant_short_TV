## MODIFIED Requirements

### Requirement: AI tasks require complete effective model billing
Before a claimed asynchronous AI episode can reserve points or contact a provider, the system SHALL resolve effective supplier cost prices and user point prices for the selected model, required usage metrics, dimensions, and execution initialization time. Both rule sets SHALL cover every required metric. Persisting a video decomposition batch and its pending episodes SHALL NOT require this billing resolution to complete synchronously.

#### Scenario: Persist a batch before execution initialization
- **WHEN** a user creates a valid video decomposition batch
- **THEN** the system durably stores its ordered pending episodes and returns the batch before resolving billing or reserving points for those episodes
- **AND** no provider call is made by the batch creation request

#### Scenario: Both model price rule sets cover the claimed episode
- **WHEN** a worker claims an episode whose model has effective cost and point components covering all required metrics
- **THEN** the system SHALL idempotently create the execution using the resolved price versions
- **AND** it may proceed with point reservation and provider dispatch

#### Scenario: Cost or point price is missing after claim
- **WHEN** a worker claims an episode whose model lacks an effective cost price or point price for any required metric
- **THEN** the system SHALL fail or recover the affected episode before provider contact
- **AND** SHALL not create a point reservation, usage line, cost line, or provider call log for that failed initialization
- **AND** SHALL retain the already-created batch and its sibling episodes
