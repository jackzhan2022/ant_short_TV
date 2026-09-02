# script-review-library Specification

## Purpose
TBD - created by archiving change optimize-script-review-workbench. Update Purpose after archive.
## Requirements
### Requirement: Independent script review library provides a project work queue
The system SHALL provide a dedicated script review library page that lists review projects returned by the existing review-project query and provides a direct path into the selected project's review workbench.

#### Scenario: View review projects in the library
- **WHEN** a user opens the script review library
- **THEN** the system displays the available independent review projects with their name, current version information, latest review round, and next-step action

#### Scenario: Open a selected project
- **WHEN** a user selects a project or its next-step action in the library
- **THEN** the system navigates to that project's review workbench
- **AND** the workbench loads the selected project rather than an arbitrary project

### Requirement: Library state is derived from existing review data
The library SHALL derive display-only work states from the existing project detail, latest review task, issues, and manual-resolution markers without changing persisted review-task or issue statuses.

#### Scenario: Identify a project requiring issue handling
- **WHEN** the latest completed task has one or more issues that are not manually resolved
- **THEN** the library displays the project as requiring issue handling
- **AND** shows the outstanding issue count when it is available

#### Scenario: Identify a project ready for re-review
- **WHEN** the latest completed task has issues and all of them are manually resolved
- **THEN** the library displays the project as ready for re-review
- **AND** does not persist this display state as a new server-side status

### Requirement: Library supports client-side project discovery
The library SHALL allow users to filter the loaded project list by name and display-derived work state without requiring a new server-side search or filter API.

#### Scenario: Filter projects by outstanding work
- **WHEN** a user selects the work-state filter for projects requiring issue handling
- **THEN** the library shows only loaded projects with that display state

#### Scenario: Search by project name
- **WHEN** a user enters a project name query
- **THEN** the library shows loaded projects whose names match the query

### Requirement: Library imports independent scripts from a modal
The system SHALL open an import modal from the script review library's new-script action and SHALL use the existing independent-script import contract.

#### Scenario: Open the independent-script import modal
- **WHEN** a user selects the new-script action in the library
- **THEN** the system opens a modal containing project-name input, supported file upload, pasted-content input, and import confirmation controls
- **AND** keeps the library list visible behind the modal

#### Scenario: Complete an independent-script import
- **WHEN** a user supplies a project name and valid supported file or pasted script content and confirms import
- **THEN** the system invokes the existing independent-script import action
- **AND** on success closes the modal, refreshes the library, and opens the newly created project's workbench

#### Scenario: Cancel an import
- **WHEN** a user closes the import modal without confirming a successful import
- **THEN** the system keeps the loaded library list and its current client-side search and filter state unchanged
