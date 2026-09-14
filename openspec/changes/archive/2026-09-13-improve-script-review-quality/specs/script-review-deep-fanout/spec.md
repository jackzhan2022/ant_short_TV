## MODIFIED Requirements

### Requirement: Persist restorable unit progress
The system SHALL persist progress independently for every required dimension discovery, semantic quality, and aggregation stage, including PENDING, RUNNING, SUCCEEDED, FAILED, STALE, and CANCELED states, Run references, attempts, errors, candidate or decision save coverage, and monotonic parent progress.

#### Scenario: User reloads during dimensional review
- **WHEN** the workbench is reopened while dimension Runs are active
- **THEN** the API reconstructs each dimension and quality stage from persisted state
- **AND** does not restart successful stages

### Requirement: Retry only failed, missing, or stale units
An authorized DEEP retry SHALL schedule new Runs only for FAILED, MISSING, or STALE dimension, semantic-quality, or aggregation stages in a still-matching snapshot unless the user explicitly requests full regeneration.

#### Scenario: One of eight dimensions fails
- **WHEN** the user retries after the timeline dimension fails
- **THEN** exactly the failed timeline stage and its dependent unfinished stages are scheduled
- **AND** successful independent dimension results remain unchanged

#### Scenario: Selected dimensions change before retry
- **WHEN** task configuration changes after a failed attempt
- **THEN** the previous snapshot is not reused
- **AND** a new attempt freezes new rules, dimensions, cache identity, and coverage without reading history

### Requirement: Aggregate only a complete unchanged snapshot
The coordinator SHALL start aggregation only after every selected dimension has successful complete coverage and every required candidate has a terminal semantic decision in the unchanged snapshot. It SHALL mark the task complete only after anomaly checks pass and aggregation successfully calls `save_review_result`.

#### Scenario: All required stages succeed
- **WHEN** all dimension and semantic-quality stages have successful terminal saves and no unresolved anomaly gate remains
- **THEN** one aggregation Run performs cross-dimension and cross-episode synthesis
- **AND** no second aggregation Run is created for the same successful attempt

#### Scenario: Zero-result anomaly remains unresolved
- **WHEN** all dimension stages return zero candidates but quality review has not completed successfully
- **THEN** aggregation cannot finalize the task
- **AND** the task remains visibly pending quality review
