## MODIFIED Requirements

### Requirement: Review tasks support asynchronous execution, progress, cancellation, and retry
The system SHALL create asynchronous Markdown review tasks for script versions, display persisted task and per-unit progress, allow task cancellation, and allow retry of failed QUICK tasks or only unsuccessful DEEP units and aggregation phases.

#### Scenario: Start a review task
- **WHEN** a user starts a review for a script version while Markdown reporting is enabled
- **THEN** the system creates an asynchronous review task and correlated unified AI execution
- **AND** exposes its review phase, result format, and persisted progress in task management

#### Scenario: Cancel a running task
- **WHEN** a user cancels a task that is waiting or running
- **THEN** the system marks the task as canceled
- **AND** preserves completed unit fragments, Agent Runs, and call records
- **AND** stops remaining scheduling and prevents finalization

#### Scenario: Retry a failed quick task
- **WHEN** an authorized user retries a failed QUICK task
- **THEN** the system reuses the same immutable script version and frozen task configuration in a new attempt
- **AND** starts a new review Agent Run

#### Scenario: Retry a partial deep task
- **WHEN** an authorized user retries a DEEP task with failed, missing, or stale units
- **THEN** the system schedules only those unsuccessful units when the snapshot still matches
- **AND** preserves matching successful Markdown fragments

### Requirement: Quick review and deep review SHALL use different review depths
The system SHALL implement QUICK as one bounded scoped Markdown-output Run and DEEP as persisted per-unit Markdown Runs followed by one cross-unit Markdown aggregation Run.

#### Scenario: Run a quick review
- **WHEN** a user starts a QUICK review whose selected scope fits the safe context budget
- **THEN** one Agent Run reviews the selected scope and dimensions
- **AND** persists its non-empty final Markdown as the report

#### Scenario: Oversized quick review
- **WHEN** a QUICK scope exceeds the safe context budget
- **THEN** the task returns an actionable size error
- **AND** asks the user to narrow scope or use DEEP rather than silently truncating content

#### Scenario: Run a deep review
- **WHEN** a user starts a DEEP review
- **THEN** the system freezes review units and runs bounded child Agents that save Markdown fragments
- **AND** starts Markdown aggregation only after complete child coverage
- **AND** applies the selected review dimensions through prompt and Skill composition

### Requirement: Review results SHALL support Markdown reports and historical structured issues
The system SHALL return an explicit result format, SHALL return the preserved Markdown text for new completed tasks, and SHALL continue returning summaries and structured issues for historical structured tasks.

#### Scenario: Produce a Markdown review report
- **WHEN** a new review task completes
- **THEN** the system returns the saved Markdown, bound version, selected dimensions, scope, mode, and execution metadata
- **AND** does not require a score, fixed outline, formal issue list, or lifecycle state

#### Scenario: View a historical structured report
- **WHEN** a user selects a historical structured task
- **THEN** the system returns its existing summary, issues, hits, and manual-resolution state
- **AND** does not convert the report into Markdown

### Requirement: The workbench SHALL support historical issue actions only for structured reports
The system SHALL keep multi-hit navigation, manual issue handling, and batch repair available for historical structured tasks and SHALL omit those issue-specific actions from Markdown tasks.

#### Scenario: Open a Markdown task
- **WHEN** the selected task result format is `MARKDOWN`
- **THEN** the workbench displays the report reader with copy and download actions
- **AND** does not show issue filters, issue detail, hit navigation, manual resolution, or batch repair

#### Scenario: Open a historical structured task
- **WHEN** the selected task result format is `STRUCTURED_JSON`
- **THEN** the workbench retains the existing issue queue, issue detail, hit navigation, and eligible issue actions

### Requirement: Review reports SHALL support version comparison and export by selected version
The system SHALL preserve both Markdown and historical structured reports permanently, support version comparison, and allow exporting reports by selected version and result format.

#### Scenario: View version comparison
- **WHEN** a user opens the history for a script
- **THEN** the system shows differences between selected versions
- **AND** keeps the associated Markdown or structured review tasks available

#### Scenario: Export a selected Markdown report
- **WHEN** the user exports a completed Markdown task
- **THEN** the system downloads the stored report text as UTF-8 Markdown
- **AND** does not reconstruct content from structured issue records

#### Scenario: Export a selected historical structured report
- **WHEN** the user exports a historical structured task
- **THEN** the existing structured report export behavior remains available

#### Scenario: Export remains tied to a specific version
- **WHEN** the user selects a different version for export
- **THEN** the exported report reflects only the chosen version and its associated review history

