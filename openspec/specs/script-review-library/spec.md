# script-review-library Specification

## Purpose
TBD - created by archiving change optimize-script-review-workbench. Update Purpose after archive.
## Requirements
### Requirement: Independent script review library provides a project work queue
The system SHALL provide a dedicated script review library page that first lists every review project visible under the current tenant and project permissions using a render-critical summary query, then enriches those rows using one non-blocking batch metrics query, while preserving the current library layout and direct path into the selected project's review history.

#### Scenario: Render review projects before metrics complete
- **WHEN** a user opens the script review library and the permission-scoped summary request completes while the metrics request remains pending
- **THEN** the system displays the available review projects with their name, source metadata, update time, and a navigation action
- **AND** metric-backed version, round, state, and issue fields display stable loading placeholders without blocking the list
- **AND** the frontend does not request full project, script-version, review-task, issue, or hit details for each listed project

#### Scenario: Enrich visible project rows
- **WHEN** the batch metrics request completes after summary rows are visible
- **THEN** the system merges current version count, latest review round, display review state, outstanding issue count, and state-specific action into matching rows by project identifier
- **AND** does not clear or replace the already rendered list while applying the enrichment

#### Scenario: Open a selected project before metrics complete
- **WHEN** a user selects a project while metrics are loading or unavailable
- **THEN** the system navigates to that project's dedicated review-history page
- **AND** the history page loads the selected project rather than an arbitrary project

#### Scenario: List permission-scoped review projects
- **WHEN** a user requests either the render-critical summaries or batch metrics
- **THEN** the response includes only unbound drafts owned by that user or visible through tenant-wide access and project-bound reviews visible through effective project access
- **AND** inaccessible or cross-tenant review projects are excluded

### Requirement: Library state is derived from existing review data
The library SHALL receive display-only work states and counts from a non-blocking batch metrics response derived from existing project, latest review task, issue, and manual-resolution data without changing persisted review-task or issue statuses and without loading long-text review entities for aggregation.

#### Scenario: Identify a project requiring issue handling
- **WHEN** the latest completed task has one or more issues that are not manually resolved and metrics enrichment completes
- **THEN** the library displays the project as requiring issue handling
- **AND** shows the outstanding issue count returned by the batch metrics response

#### Scenario: Identify a project ready for re-review
- **WHEN** the latest completed task has issues and all of them are manually resolved and metrics enrichment completes
- **THEN** the library displays the project as ready for re-review
- **AND** does not persist this display state as a new review-task status

#### Scenario: Derive metrics for multiple projects
- **WHEN** the library contains multiple accessible review projects
- **THEN** the backend obtains version, latest-task, and outstanding-issue metrics with a bounded number of aggregate or projection queries
- **AND** query count does not increase with project count
- **AND** the queries do not select script bodies, report bodies, issue evidence, excerpts, problems, or suggestions

#### Scenario: Metrics enrichment fails
- **WHEN** the summary list has rendered and its metrics request fails
- **THEN** the system keeps project rows, name search, navigation, and import actions available
- **AND** identifies metric-backed fields as unavailable and offers a metrics-only retry

### Requirement: Library supports client-side project discovery
The library SHALL allow users to search the loaded summary list by project name immediately and SHALL enable filtering by server-derived work state after the non-blocking metrics response is available, without requiring server-side search or per-filter requests.

#### Scenario: Search by project name while metrics load
- **WHEN** a user enters a project name query after summaries render and before metrics complete
- **THEN** the library shows loaded project summaries whose names match the query

#### Scenario: Work-state filters await metrics
- **WHEN** summaries are visible but metrics are still loading
- **THEN** the library presents work-state counters and filters in an explicit loading state
- **AND** does not classify projects using guessed or stale work states

#### Scenario: Filter projects by outstanding work
- **WHEN** metrics have completed and a user selects the work-state filter for projects requiring issue handling
- **THEN** the library shows only loaded project summaries enriched with that display state
- **AND** does not send a server request for the filter change

### Requirement: Library imports independent scripts from a modal
The system SHALL open an import modal from the script review library's new-script action, SHALL use the existing independent-script import contract, and SHALL refresh progressive library data after a successful import.

#### Scenario: Open the independent-script import modal
- **WHEN** a user selects the new-script action in the library
- **THEN** the system opens a modal containing project-name input, supported file upload, pasted-content input, and import confirmation controls
- **AND** keeps the loaded library list visible behind the modal

#### Scenario: Complete an independent-script import
- **WHEN** a user supplies a project name and valid supported file or pasted script content and confirms import
- **THEN** the system invokes the existing independent-script import action
- **AND** on success closes the modal, refreshes and renders the summary list, starts a non-blocking metrics refresh, and opens the newly created project's review-history page

#### Scenario: Cancel an import
- **WHEN** a user closes the import modal without confirming a successful import
- **THEN** the system keeps the loaded library list and its current client-side search and filter state unchanged
