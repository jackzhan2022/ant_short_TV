## ADDED Requirements

### Requirement: Execute scoped episode units with bounded concurrency
The system SHALL execute independent episode units in a scoped asset re-extraction concurrently up to the configured per-operation workflow fan-out limit. It MUST NOT hold a database transaction open during an Agent or provider call.

#### Scenario: Re-extract a multi-episode script
- **WHEN** an authorized user starts scoped re-extraction for more than one frozen active episode
- **THEN** the system may run multiple episode Agent children concurrently
- **AND** active children for that operation do not exceed the configured workflow fan-out limit

#### Scenario: Configured concurrency exceeds remaining work
- **WHEN** fewer runnable episode units remain than the configured concurrency
- **THEN** the system starts no more workers than runnable units
- **AND** does not create duplicate child Runs for the same unit attempt

### Requirement: Isolate concurrent unit outcomes and preserve recovery
The system SHALL persist each concurrent unit claim and outcome independently, retain successful formal commits, and make only pending, failed, or interrupted units runnable after retry or execution recovery.

#### Scenario: One concurrent child fails
- **WHEN** one child Run fails while peer children succeed
- **THEN** the failed unit records its actionable error without reverting successful peer units
- **AND** the parent operation fails only after submitted peer work has settled

#### Scenario: Worker loses execution ownership
- **WHEN** the active execution claim is lost while concurrent children are running
- **THEN** the worker stops scheduling useful new work and does not finalize the snapshot
- **AND** late writes from the stale attempt are rejected by ownership fencing
- **AND** a replacement attempt reuses committed units and schedules only unfinished units

#### Scenario: User cancels concurrent re-extraction
- **WHEN** cancellation is requested while episode children are pending or running
- **THEN** the system stops scheduling useful new children and cancels the parent operation
- **AND** it does not retire old assets or discard already committed formal results

### Requirement: Finalize concurrent re-extraction only after complete coverage
The system SHALL finalize a scoped re-extraction only after every frozen episode unit has a successful formal save under the current source snapshot and owning execution attempt.

#### Scenario: All concurrent units succeed
- **WHEN** every frozen episode unit has successful commit evidence after concurrent execution settles
- **THEN** the system performs scoped retirement exactly once
- **AND** marks the snapshot, operation, and unified execution successful

#### Scenario: Any unit remains incomplete
- **WHEN** any frozen unit is failed, pending, interrupted, or missing formal commit evidence
- **THEN** the system does not retire old assets
- **AND** exposes persisted completed and failed counts for a targeted retry
