## ADDED Requirements

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

## MODIFIED Requirements

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
