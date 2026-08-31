## MODIFIED Requirements

### Requirement: Review tasks support asynchronous execution, progress, cancellation, and retry
The system SHALL create asynchronous workflow-Agent review tasks for script versions, display persisted task and per-unit progress, allow task cancellation, and allow retry of failed QUICK tasks or only unsuccessful DEEP units and aggregation phases.

#### Scenario: Start a review task
- **WHEN** a user starts a review for a script version
- **THEN** the system creates an asynchronous review task and correlated unified AI execution
- **AND** exposes its workflow Agent phase and persisted progress in task management

#### Scenario: Cancel a running task
- **WHEN** a user cancels a task that is waiting or running
- **THEN** the system marks the task as canceled
- **AND** preserves completed unit candidates, formal stage results, Agent Runs, and call records
- **AND** stops remaining scheduling and prevents finalization

#### Scenario: Retry a failed quick task
- **WHEN** an authorized user retries a failed QUICK task
- **THEN** the system reuses the same immutable script version and frozen task configuration in a new attempt
- **AND** starts a new review Agent Run

#### Scenario: Retry a partial deep task
- **WHEN** an authorized user retries a DEEP task with failed, missing, or stale units
- **THEN** the system schedules only those unsuccessful units when the snapshot still matches
- **AND** preserves matching successful unit candidates

### Requirement: Review configuration supports manual dimensions, scope, and mode selection
The system SHALL allow the user to manually select one or more review dimensions, choose ALL, EPISODES, or SCENES scope, and choose QUICK or DEEP before execution. The system SHALL lock the configuration for an active attempt and SHALL enforce the selected scope in trusted content reads.

#### Scenario: Select review dimensions
- **WHEN** a user creates a review task
- **THEN** the system allows selecting one or more of the thirteen registered review dimensions
- **AND** maps them to trusted dimension Skills without accepting client-supplied Skill codes

#### Scenario: Select episode scope
- **WHEN** a user chooses specific episodes
- **THEN** every review Run and tool read is limited to those episodes

#### Scenario: Select scene scope
- **WHEN** a user chooses specific scenes
- **THEN** every review Run and tool read is limited to those scenes
- **AND** the system does not silently review the complete script

#### Scenario: Switch review mode before execution
- **WHEN** a task has not entered a running attempt
- **THEN** the user can change the selected dimensions, scope, or review mode
- **AND** once an attempt runs, its configuration and hashes remain frozen

### Requirement: Quick review and deep review SHALL use different review depths
The system SHALL implement QUICK as one bounded scoped workflow Agent Run and DEEP as persisted per-unit child Runs followed by one cross-unit aggregation Run that adds global continuity and round comparison.

#### Scenario: Run a quick review
- **WHEN** a user starts a QUICK review whose selected scope fits the safe context budget
- **THEN** one Agent Run reviews the selected scope and dimensions
- **AND** prioritizes evident local issues without promising complete cross-unit discovery

#### Scenario: Oversized quick review
- **WHEN** a QUICK scope exceeds the safe context budget
- **THEN** the task returns an actionable size error
- **AND** asks the user to narrow scope or use DEEP rather than silently truncating content

#### Scenario: Run a deep review
- **WHEN** a user starts a DEEP review
- **THEN** the system freezes review units and runs bounded child Agents
- **AND** starts cross-unit aggregation only after complete child coverage
- **AND** includes character, timeline, scene, prop, visual, emotion, suspense, reversal, foreshadowing, causal, and round checks required by the selected dimensions

### Requirement: Review results SHALL be structured by issue and support multi-round status tracking
The system SHALL create formal review results only through a successful atomic terminal save and SHALL return an overall conclusion, score, actual coverage, selected dimensions, and structured issues with stable server-assigned identity and multi-round lifecycle.

#### Scenario: Produce a structured review report
- **WHEN** a review task completes
- **THEN** the system returns an overall conclusion, score, selected dimensions, actual coverage, and formal issue list
- **AND** each issue includes a server-assigned issue number, severity, dimension, title, location, verified excerpt, problem description, evidence, suggestion, status, and one or more hits when applicable

#### Scenario: Track issue status across rounds
- **WHEN** a new review round completes after editing
- **THEN** deterministic server matching creates new round issue numbers and links safe prior identities
- **AND** computes `new`, `persists`, `fixed`, `shifted`, or `uncertain`
- **AND** preserves every prior round and event

#### Scenario: Preserve manual resolved markers
- **WHEN** a user marks an issue as resolved
- **THEN** the system keeps the issue in the processed area and records a manual-resolution event
- **AND** does not let a later model overwrite that historical event

#### Scenario: Reopen a manually resolved issue during a later review
- **WHEN** a later formal result safely matches an issue that was manually resolved
- **THEN** the new round marks the issue `persists`
- **AND** keeps the earlier manual resolved marker in issue history

#### Scenario: Final save validation fails
- **WHEN** any issue contains an unselected dimension, invalid severity, absent evidence, foreign location, duplicate identity, stale hash, or incomplete DEEP coverage
- **THEN** no part of the new formal report is committed
- **AND** the prior review history remains unchanged
