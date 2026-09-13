## MODIFIED Requirements

### Requirement: Persist restorable unit progress
The system SHALL persist PENDING, RUNNING, SUCCEEDED, FAILED, STALE, and CANCELED unit states, child Run references, attempts, errors, Markdown-fragment completion, and monotonic parent progress.

#### Scenario: User reloads during deep review
- **WHEN** the workbench is reopened while child Runs are active
- **THEN** the API reconstructs total, completed, failed, current, and pending units from persisted state
- **AND** does not restart units with successfully saved Markdown fragments

### Requirement: Retry only failed, missing, or stale units
An authorized DEEP retry SHALL schedule new child Runs only for FAILED, MISSING, or STALE units in a still-matching snapshot unless the user explicitly requests full regeneration.

#### Scenario: One of fifty-eight units fails
- **WHEN** the user retries the partial failure
- **THEN** exactly one new child Run is scheduled
- **AND** fifty-seven successful Markdown fragments remain unchanged

#### Scenario: Selected dimensions change before retry
- **WHEN** task configuration changes after a failed attempt
- **THEN** the previous snapshot is not reused
- **AND** a new attempt freezes new Skill and unit coverage

### Requirement: Aggregate only a complete unchanged snapshot
The coordinator SHALL start one cross-unit aggregation Run only after every unit in the unchanged snapshot has a non-empty saved Markdown fragment and SHALL mark the task complete only after the aggregation Run's non-empty Markdown is persisted as the task report.

#### Scenario: All child Runs succeed
- **WHEN** all frozen units have successfully saved Markdown fragments
- **THEN** one aggregation Run receives the ordered fragments and performs cross-unit synthesis
- **AND** no second aggregation Run is created for the same successful attempt

#### Scenario: Aggregation Run fails
- **WHEN** all children succeed but the aggregation Run fails or returns empty output
- **THEN** the task remains retryable and not completed
- **AND** a retry reuses the matching child fragments and reruns only aggregation

### Requirement: Cancel pending deep-review work safely
Cancellation SHALL prevent new child or aggregation scheduling, mark pending or running units canceled as appropriate, preserve completed Markdown fragments and audit records, and prevent final report persistence.

#### Scenario: User cancels with active children
- **WHEN** cancellation is requested while child Runs are active
- **THEN** the coordinator stops scheduling pending units and records cancellation
- **AND** the snapshot cannot finalize after late child completion

