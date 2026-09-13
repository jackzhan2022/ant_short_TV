## ADDED Requirements

### Requirement: Fan-out recovery is attempt-safe and preserves successes
The script-analysis fan-out coordinator SHALL reuse a compatible persisted snapshot after execution interruption, MUST keep successful episode units unchanged, and SHALL make only pending, failed, or stale units runnable by the replacement attempt.

#### Scenario: Execution expires while episode units are running
- **WHEN** a fan-out execution attempt loses ownership with some units successful, some running, and some not started
- **THEN** the compatible replacement attempt retains every successful unit
- **AND** marks interrupted running units stale for retry
- **AND** schedules only pending, failed, or stale units

#### Scenario: Stale attempt finishes after replacement attempt starts
- **WHEN** a child or parent call from the stale attempt returns after a replacement attempt owns the execution
- **THEN** the stale attempt does not overwrite the replacement attempt's unit, stage, or task state

### Requirement: Parent progress reflects the authoritative recovery attempt
The system SHALL derive parent stage and fan-out snapshot counts from persisted episode-unit states and SHALL avoid exposing a terminal parent failure solely from a stale attempt while a replacement attempt is running.

#### Scenario: Client polls during automatic recovery
- **WHEN** a replacement execution attempt is actively retrying unsuccessful units
- **THEN** the parent analysis state reports active recovery with persisted completed, running, pending, and failed counts
- **AND** it does not report the stale attempt as the authoritative terminal outcome

#### Scenario: Recovery reaches complete coverage
- **WHEN** every unit in the unchanged snapshot has a successful terminal save
- **THEN** the finalizer runs once for the authoritative attempt
- **AND** the snapshot, stage, task, and unified execution reach successful terminal states
