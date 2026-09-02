## ADDED Requirements

### Requirement: Workbench presents a focused issue-resolution layout
The system SHALL present the selected review task with a problem queue, script content area, and selected-problem detail area in a coordinated workbench layout on desktop-sized viewports.

#### Scenario: Select a visible review issue
- **WHEN** a user selects an unresolved issue from the problem queue
- **THEN** the system displays that issue's severity, dimension, description, excerpt, suggestions, and available actions in the detail area
- **AND** visually identifies the selected issue in the queue

#### Scenario: Use the workbench on a constrained viewport
- **WHEN** the available viewport cannot safely display three columns
- **THEN** the system provides access to the issue queue and issue detail without overlapping the script content

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
