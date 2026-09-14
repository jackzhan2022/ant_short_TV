# script-review-workbench Specification

## Purpose
TBD - created by archiving change add-script-review-workbench. Update Purpose after archive.
## Requirements
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
The system SHALL keep every review round and its report history available on the dedicated project review-history page even after the script advances to later versions, and SHALL load a report's full details only after that review round is selected.

#### Scenario: Open a later script version
- **WHEN** a user opens a project whose script has advanced to a newer version
- **THEN** the review-history page still lists earlier review rounds with their associated version metadata
- **AND** the earlier reports remain individually viewable and exportable from their dedicated task-detail pages

### Requirement: Workbench presents a focused issue-resolution layout
The dedicated review-detail page SHALL present only the selected review task with a problem queue, its bound script-version content area, and selected-problem detail area in a coordinated workbench layout on desktop-sized viewports.

#### Scenario: Select a visible review issue
- **WHEN** a user selects an unresolved issue from the selected task's problem queue
- **THEN** the system displays that issue's severity, dimension, description, excerpt, suggestions, and available actions in the detail area
- **AND** visually identifies the selected issue in the queue
- **AND** does not request another project's or task's issue data

#### Scenario: Use the workbench on a constrained viewport
- **WHEN** the available viewport cannot safely display three columns
- **THEN** the system provides access to the selected task's issue queue and issue detail without overlapping its bound script content

### Requirement: Workbench locates selected issue evidence in script content
The system SHALL use the selected issue's existing excerpt and hit data to focus the available matching text in the script content area, without inventing server-side line identifiers.

#### Scenario: Locate a matching issue hit
- **WHEN** a user selects a hit for an issue whose excerpt occurs in the displayed script version
- **THEN** the system focuses the script content and highlights the matching text

#### Scenario: Excerpt cannot be located
- **WHEN** no selected issue excerpt can be matched in the displayed script content
- **THEN** the system keeps the issue detail available
- **AND** indicates that the source location cannot be highlighted in the current version

### Requirement: Workbench provides a review-task configuration modal
The system SHALL open review task configuration in a modal on demand from the workbench and SHALL preserve the existing version, review mode, selected dimensions, scope, validation, and task-creation behavior.

#### Scenario: Start a review with valid configuration
- **WHEN** a user confirms an eligible version, one or more dimensions, mode, and required scope values in the configuration drawer
- **THEN** the system creates a review task using the existing task-creation contract
- **AND** presents the created task's existing execution progress in the workbench

#### Scenario: Close task configuration without creating a task
- **WHEN** a user closes the task configuration modal before submission
- **THEN** the system preserves the selected workbench task, version, script location, and visible issue detail

#### Scenario: Validate a scoped review before creation
- **WHEN** a user chooses episode or scene scope without scope values
- **THEN** the system prevents task creation
- **AND** identifies the missing scope input

### Requirement: Workbench keeps existing version and issue actions reachable
The system SHALL keep existing version save, rollback, report export, manual resolution, batch repair, cancellation, and retry actions reachable after the workbench layout changes.

#### Scenario: Preview a supported batch repair
- **WHEN** a user selects a supported batch repair action for one or more issue hits
- **THEN** the system presents the selected hit set and replacement effect before the user confirms the existing batch repair action

#### Scenario: Preserve manual-resolution behavior
- **WHEN** a user marks an issue as handled from the selected issue detail
- **THEN** the system invokes the existing manual-resolution action
- **AND** refreshes the visible issue queue using the returned project detail

### Requirement: Review navigation separates project history from task detail
The system SHALL provide a dedicated review-history page for one review project and a dedicated review-detail page for one selected review task, and each page SHALL load only data required by its own level.

#### Scenario: Open project review history
- **WHEN** a user opens an accessible review project's history page
- **THEN** the system loads lightweight project header and version metadata plus one history row per review attempt
- **AND** does not load script-version bodies, issue details, hit details, fanout units, or observability details

#### Scenario: Open a selected review attempt
- **WHEN** a user selects a review-history row
- **THEN** the system navigates to a dedicated route for that task
- **AND** loads the selected task, its bound script version, and that task's review details
- **AND** does not load other tasks or other script-version bodies

#### Scenario: Open a legacy project workbench link
- **WHEN** a user opens a supported legacy `/script-review` link containing a project identifier
- **THEN** the system redirects to that project's review-history page
- **AND** preserves the identified project

### Requirement: Review history lists every attempt independently
The review-history page SHALL display one row per review attempt ordered newest first, including attempts for the same script version and attempts in pending, running, failed, canceled, or completed states.

#### Scenario: Display repeated reviews of one version
- **WHEN** the same script version has been reviewed more than once
- **THEN** the history page displays every review attempt as a separate row
- **AND** identifies each row by its task and round

#### Scenario: Display operational task states
- **WHEN** a project has pending, running, failed, canceled, and completed review attempts
- **THEN** the history page keeps every attempt visible with its persisted status
- **AND** exposes valid follow-up actions such as viewing progress, retrying, or opening completed results according to that status

#### Scenario: Display review-history fields
- **WHEN** the history page renders a review attempt
- **THEN** its row includes script version, review round, mode, scope, selected dimensions, status, progress, issue totals, unresolved totals, creator, creation time, and terminal time or concise failure information when applicable

#### Scenario: Page through review history
- **WHEN** a project has more review attempts than the configured history page size
- **THEN** the system returns stable pages ordered by creation time and task identifier descending
- **AND** obtaining a page does not load issue or hit entities for each row

### Requirement: Page-scoped reads preserve review authorization
Library, history, and task-detail reads SHALL apply the existing creator ownership and effective project-access rules independently and SHALL fail closed for inaccessible or cross-tenant identifiers.

#### Scenario: Creator opens an unbound review history
- **WHEN** the creator opens the history or task detail for their unbound review draft
- **THEN** the system permits the read under the existing creator ownership rule

#### Scenario: Non-owner opens an unbound review history
- **WHEN** another ordinary tenant member requests the history or task detail of an unbound review draft
- **THEN** the system returns an authorization failure without disclosing review metadata

#### Scenario: Project member opens a bound review task
- **WHEN** an active member with effective view access requests a task belonging to a bound review project
- **THEN** the system returns only that selected task and its bound version data

#### Scenario: Cross-tenant task identifier
- **WHEN** a user supplies a task identifier owned by another tenant
- **THEN** the system fails closed and does not fall back to another task or project

