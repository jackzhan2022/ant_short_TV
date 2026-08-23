## ADDED Requirements

### Requirement: Independent script review workbench supports script import and versioned drafts
The system SHALL provide an independent script review workbench that accepts imported `Word`, `TXT`, or `Markdown` script files and SHALL preserve the original imported script as the first immutable version.

#### Scenario: Import a supported script file
- **WHEN** a user imports a `Word`, `TXT`, or `Markdown` script into the review workbench
- **THEN** the system creates a review project with the imported content as the first script version
- **AND** preserves the original imported content for later reference

#### Scenario: Create a new draft version after editing
- **WHEN** a user edits the script and saves the result
- **THEN** the system creates a new script version
- **AND** keeps the prior version available in version history

### Requirement: Review tasks support asynchronous execution, progress, cancellation, and retry
The system SHALL create asynchronous review tasks for script versions, display task progress, allow task cancellation, and allow retry of failed tasks.

#### Scenario: Start a review task
- **WHEN** a user starts a review for a script version
- **THEN** the system creates an asynchronous review task
- **AND** exposes its progress state in task management

#### Scenario: Cancel a running task
- **WHEN** a user cancels a task that is waiting or running
- **THEN** the system marks the task as canceled
- **AND** preserves completed stage results and call records
- **AND** stops remaining review execution

#### Scenario: Retry a failed task
- **WHEN** a task fails
- **THEN** an authorized user can retry the failed task
- **AND** the system reuses the same script version as the review input

### Requirement: Review configuration supports manual dimensions, scope, and mode selection
The system SHALL allow the user to manually select review dimensions, choose a review scope, and choose either quick review or deep review before the task starts.

#### Scenario: Select review dimensions
- **WHEN** a user creates a review task
- **THEN** the system allows selecting one or more dimensions such as dialogue rationality, character relationship consistency, prop continuity, scene continuity, plot logic, timeline, motivation, visual continuity, or storyboard executability

#### Scenario: Select a limited review scope
- **WHEN** a user chooses to review only certain episodes or scenes
- **THEN** the system limits the review task to the selected scope

#### Scenario: Switch review mode before execution
- **WHEN** a task has not yet entered the running state
- **THEN** the user can change the selected dimensions, scope, or review mode
- **AND** once the task enters running state, the configuration becomes locked

### Requirement: Quick review and deep review SHALL use different review depths
The system SHALL treat quick review as a scoped, fast review path and deep review as a full review path that adds global indexing, cross-episode checks, and round comparison.

#### Scenario: Run a quick review
- **WHEN** a user starts a quick review
- **THEN** the system reviews the selected scope and selected dimensions
- **AND** prioritizes obvious local issues
- **AND** does not promise complete cross-episode issue discovery

#### Scenario: Run a deep review
- **WHEN** a user starts a deep review
- **THEN** the system performs the global indexing pass
- **AND** reviews the selected dimensions against the global index
- **AND** includes cross-episode consistency and round comparison in the result

### Requirement: Review results SHALL be structured by issue and support multi-round status tracking
The system SHALL return structured review issues with stable issue fields, round numbers, severity, dimension, excerpt, reason, suggestion, and status tracking for multi-round revision.

#### Scenario: Produce a structured review report
- **WHEN** a review task completes
- **THEN** the system returns an overall conclusion, score, selected dimensions, and a list of structured issues
- **AND** each issue includes an issue number, severity, dimension, title, location, excerpt, problem description, evidence, suggestion, and status

#### Scenario: Track issue status across rounds
- **WHEN** a new review round starts after editing
- **THEN** the system creates new issue numbers for the new round
- **AND** preserves the prior round history for comparison
- **AND** can mark issues as `new`, `persists`, `fixed`, `shifted`, or `uncertain`

#### Scenario: Preserve manual resolved markers
- **WHEN** a user marks an issue as resolved
- **THEN** the system moves the issue into the processed area
- **AND** preserves the manual marker history for later review

#### Scenario: Reopen a manually resolved issue during a later review
- **WHEN** a later review still matches an issue that was manually marked as resolved
- **THEN** the system changes the issue status back to `persists`
- **AND** keeps the earlier manual resolved marker in the issue history

### Requirement: The workbench SHALL support multi-hit issue aggregation and batch repair for basic editing actions
The system SHALL allow one issue to map to multiple matched text fragments and SHALL support batch repair actions for global replacement, batch insertion, and batch deletion.

#### Scenario: Aggregate multiple matching fragments under one issue
- **WHEN** the same issue appears in multiple script fragments
- **THEN** the system groups those fragments under a single issue record
- **AND** shows the matched fragments to the user for bulk review

#### Scenario: Apply a batch repair after confirmation
- **WHEN** a user selects multiple matched fragments and confirms a batch repair preview
- **THEN** the system applies the approved replacement, insertion, or deletion to the draft version
- **AND** records the batch repair action for history and rollback

#### Scenario: Restore a previous version
- **WHEN** a user requests rollback
- **THEN** the system restores a previously saved script version
- **AND** keeps the restored-from version in history

### Requirement: Review reports SHALL support version comparison and export by selected version
The system SHALL preserve review reports permanently, support version comparison, and allow exporting reports by selected version.

#### Scenario: View version comparison
- **WHEN** a user opens the history for a script
- **THEN** the system shows differences between selected versions
- **AND** keeps the associated review tasks and issue mappings available

#### Scenario: Export a selected version report
- **WHEN** a user exports a report for a selected version
- **THEN** the system includes the task number, round number, issue mapping, and historical comparison data for that version

#### Scenario: Export remains tied to a specific version
- **WHEN** the user selects a different version for export
- **THEN** the exported report reflects only the chosen version and its associated review history

### Requirement: Review report history SHALL remain available after later edits
The system SHALL keep every review round and its report history available even after the script advances to later versions.

#### Scenario: Open a later script version
- **WHEN** a user opens a newer script version
- **THEN** the system still keeps earlier review rounds available in history
- **AND** the earlier reports remain viewable and exportable
