# script-analysis-agent-fanout Specification

## Purpose
TBD - created by archiving change add-remaining-short-drama-analysis-agents. Update Purpose after archive.
## Requirements
### Requirement: Fan out episode-scoped Agent Runs from one analysis stage
The system SHALL coordinate episode summary and asset recognition as script-level stage attempts containing one child workflow Agent Run for each active episode in a frozen episode-set snapshot.

#### Scenario: Start a stage for twenty episodes
- **WHEN** the stage starts with twenty active episodes
- **THEN** the coordinator records total units as twenty
- **AND** schedules one scoped child Run per episode without requiring one model conversation to perform twenty saves

### Requirement: Bound concurrency and freeze stage configuration
The coordinator SHALL enforce a shared configurable provider/model concurrency limit across summary, recognition and storyboard workers, including multiple service instances, and SHALL freeze Agent, Skill, model and episode snapshots per attempt. Each stage SHALL have independently configurable run and request budgets, with request timeout bounded by remaining run time. Queue time SHALL be measured separately and not consume active run budget.

#### Scenario: Agent configuration changes during a stage
- **WHEN** an administrator publishes a new Agent or Skill revision while children remain pending
- **THEN** existing children retain frozen configuration
- **AND** later explicit runs can use the new revision

#### Scenario: Multiple branches compete for capacity
- **WHEN** summary, recognition and storyboard requests exceed the shared limit
- **THEN** excess work remains recoverably queued and active requests do not exceed the configured quota
- **AND** bounded priority prevents indefinite starvation

#### Scenario: Second request exhausts remaining time
- **WHEN** earlier work consumes part of a stage budget
- **THEN** the next request uses at most the remaining budget and timeout diagnostics identify the limiting budget

### Requirement: Aggregate persisted per-episode progress
The coordinator SHALL derive completed, failed, running, and pending unit counts from server-side child Run and formal-save state and SHALL update the parent analysis stage monotonically.

#### Scenario: User leaves and reopens the page
- **WHEN** the page reloads during a fan-out stage
- **THEN** the API reconstructs total, completed, current, and failed episode units from persisted state
- **AND** does not restart successful child Runs

### Requirement: Retry only failed or stale episode units
An authorized retry SHALL create new child Runs only for failed, missing, or stale episode units in the current stage snapshot unless the user explicitly requests full regeneration.

#### Scenario: Two of twenty summaries fail
- **WHEN** the user retries the summary stage
- **THEN** the coordinator schedules the two unsuccessful episode units
- **AND** leaves eighteen committed summaries unchanged

#### Scenario: Episode set changes before retry
- **WHEN** splitting changes the active episode set before a retry
- **THEN** the coordinator creates a new stage snapshot
- **AND** does not attach old episode-unit success to unrelated new episodes

### Requirement: Finalize only complete current snapshots
The coordinator SHALL invoke a stage finalizer and mark the parent stage succeeded only when every unit in the unchanged current snapshot has a successful terminal save.

#### Scenario: Snapshot becomes stale before finalization
- **WHEN** an episode is added, retired, or changes content after child Runs started
- **THEN** finalization fails with a stale-snapshot result
- **AND** the parent stage is not marked succeeded
