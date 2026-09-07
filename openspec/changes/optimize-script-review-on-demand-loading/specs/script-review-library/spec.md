## MODIFIED Requirements

### Requirement: Independent script review library provides a project work queue
The system SHALL provide a dedicated script review library page that lists every review project visible under the current tenant and project permissions using one lightweight project-summary query, preserves the current library layout, and provides a direct path into the selected project's review history.

#### Scenario: View review projects in the library
- **WHEN** a user opens the script review library
- **THEN** the system displays the available independent review projects with their name, current version information, latest review round, display review state, outstanding issue count, and next-step action
- **AND** the frontend does not request full project, script-version, review-task, issue, or hit details for each listed project

#### Scenario: Open a selected project
- **WHEN** a user selects a project or its next-step action in the library
- **THEN** the system navigates to that project's dedicated review-history page
- **AND** the history page loads the selected project rather than an arbitrary project

#### Scenario: List permission-scoped review projects
- **WHEN** a user requests the script review library
- **THEN** the response includes only unbound drafts owned by that user and project-bound reviews visible through effective project access
- **AND** inaccessible or cross-tenant review projects are excluded

### Requirement: Library state is derived from existing review data
The library SHALL receive display-only work states and counts derived by the server from existing project, latest review task, issue, and manual-resolution data without changing persisted review-task or issue statuses and without loading project details in the client.

#### Scenario: Identify a project requiring issue handling
- **WHEN** the latest completed task has one or more issues that are not manually resolved
- **THEN** the library displays the project as requiring issue handling
- **AND** shows the outstanding issue count returned by the lightweight project summary

#### Scenario: Identify a project ready for re-review
- **WHEN** the latest completed task has issues and all of them are manually resolved
- **THEN** the library displays the project as ready for re-review
- **AND** does not persist this display state as a new review-task status

#### Scenario: Derive summaries for multiple projects
- **WHEN** the library contains multiple accessible review projects
- **THEN** the backend obtains their version, latest-task, and outstanding-issue aggregates with a bounded number of batch queries
- **AND** response mapping does not execute one query per project

### Requirement: Library supports client-side project discovery
The library SHALL allow users to filter the loaded lightweight project list by name and server-derived work state without requiring a new server-side search or filter API.

#### Scenario: Filter projects by outstanding work
- **WHEN** a user selects the work-state filter for projects requiring issue handling
- **THEN** the library shows only loaded project summaries with that display state

#### Scenario: Search by project name
- **WHEN** a user enters a project name query
- **THEN** the library shows loaded project summaries whose names match the query

### Requirement: Library imports independent scripts from a modal
The system SHALL open an import modal from the script review library's new-script action and SHALL use the existing independent-script import contract.

#### Scenario: Open the independent-script import modal
- **WHEN** a user selects the new-script action in the library
- **THEN** the system opens a modal containing project-name input, supported file upload, pasted-content input, and import confirmation controls
- **AND** keeps the library list visible behind the modal

#### Scenario: Complete an independent-script import
- **WHEN** a user supplies a project name and valid supported file or pasted script content and confirms import
- **THEN** the system invokes the existing independent-script import action
- **AND** on success closes the modal, refreshes the lightweight library list, and opens the newly created project's review-history page

#### Scenario: Cancel an import
- **WHEN** a user closes the import modal without confirming a successful import
- **THEN** the system keeps the loaded library list and its current client-side search and filter state unchanged
