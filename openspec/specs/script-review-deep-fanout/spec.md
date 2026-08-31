# script-review-deep-fanout Specification

## Purpose
TBD - created by archiving change upgrade-script-review-workflow-agent. Update Purpose after archive.
## Requirements
### Requirement: Freeze a persisted deep-review unit snapshot
The system SHALL create a persisted DEEP snapshot containing the immutable version hash, scope hash, unit-set hash, selected dimensions, Agent revision, ordered Skill revisions, model, attempt, and review units before scheduling child Runs.

#### Scenario: Deep review has explicit episodes
- **WHEN** the selected scope resolves to twenty explicit episodes
- **THEN** the snapshot contains twenty ordered units with stable fingerprints
- **AND** each unit remains review-only scope tied to the immutable review version

#### Scenario: Deep review has no episode headings
- **WHEN** the trusted parser cannot identify reliable episode units
- **THEN** the planner creates bounded review units from trusted scene, paragraph, and character-offset boundaries
- **AND** does not persist them as formal episodes

### Requirement: Bound child concurrency and isolate one unit per Run
The DEEP coordinator SHALL run at most the configured number of child Runs concurrently and SHALL provide exactly one frozen review unit to each child Run.

#### Scenario: Fifty-eight units use concurrency three
- **WHEN** a snapshot contains fifty-eight runnable units and concurrency is three
- **THEN** no more than three child Runs execute concurrently
- **AND** every child uses the same frozen Agent, Skills, model, dimensions, and snapshot

### Requirement: Persist restorable unit progress
The system SHALL persist PENDING, RUNNING, SUCCEEDED, FAILED, STALE, and CANCELED unit states, child Run references, attempts, errors, candidate-save coverage, and monotonic parent progress.

#### Scenario: User reloads during deep review
- **WHEN** the workbench is reopened while child Runs are active
- **THEN** the API reconstructs total, completed, failed, current, and pending units from persisted state
- **AND** does not restart successful units

### Requirement: Retry only failed, missing, or stale units
An authorized DEEP retry SHALL schedule new child Runs only for FAILED, MISSING, or STALE units in a still-matching snapshot unless the user explicitly requests full regeneration.

#### Scenario: One of fifty-eight units fails
- **WHEN** the user retries the partial failure
- **THEN** exactly one new child Run is scheduled
- **AND** fifty-seven successful candidate results remain unchanged

#### Scenario: Selected dimensions change before retry
- **WHEN** task configuration changes after a failed attempt
- **THEN** the previous snapshot is not reused
- **AND** a new attempt freezes new Skill and unit coverage

### Requirement: Detect stale version, scope, and unit sets
The coordinator SHALL refuse reuse or finalization when the current immutable version identity, content hash, scope hash, selected dimensions, or unit-set hash differs from the snapshot.

#### Scenario: Scope changes during execution
- **WHEN** an authorized configuration change produces a different scope hash
- **THEN** the active snapshot becomes stale
- **AND** its candidate results cannot be promoted to a formal report

### Requirement: Aggregate only a complete unchanged snapshot
The coordinator SHALL start one cross-unit aggregation Run only after every unit in the unchanged snapshot has a successful candidate save and SHALL mark the task complete only after the aggregation Run successfully calls `save_review_result`.

#### Scenario: All child Runs succeed
- **WHEN** all frozen units have successful candidate-save coverage
- **THEN** one aggregation Run reads their candidates and performs cross-unit synthesis
- **AND** no second aggregation Run is created for the same successful attempt

#### Scenario: Aggregation Run fails
- **WHEN** all children succeed but the aggregation Run fails
- **THEN** the task remains retryable and not completed
- **AND** a retry reuses the matching child results and reruns only aggregation

### Requirement: Cancel pending deep-review work safely
Cancellation SHALL prevent new child or aggregation scheduling, mark pending or running units canceled as appropriate, preserve completed candidates and audit records, and prevent final formal save.

#### Scenario: User cancels with active children
- **WHEN** cancellation is requested while child Runs are active
- **THEN** the coordinator stops scheduling pending units and records cancellation
- **AND** the snapshot cannot finalize after late child completion

### Requirement: Correlate fan-out usage and settlement
All child and aggregation model calls SHALL correlate to the parent review task and unified AI execution, and final settlement SHALL use aggregated actual usage with idempotent outcome handling.

#### Scenario: Partial failure consumes provider usage
- **WHEN** some child calls succeed and one provider-billed call fails
- **THEN** every call remains auditable under the parent execution
- **AND** retry settlement does not double-charge already settled usage

