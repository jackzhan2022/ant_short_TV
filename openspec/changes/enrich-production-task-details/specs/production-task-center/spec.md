## ADDED Requirements

### Requirement: Task center follows the package management page layout
The task page SHALL follow the package management page's PageContainer, scope Tabs, ProTable search/table/toolbar/pagination and right-side Drawer arrangement. It SHALL use consistent primary/secondary typography, status tags, readable time/progress, explicit view-detail actions and loading/empty/error states. It MUST preserve existing scope permissions and controls, default to mine, and MUST NOT introduce package creation or publishing actions. Layout reuse MUST NOT replace server-side filtering and pagination with full-dataset client slicing.

#### Scenario: Submit or reset task filters
- **WHEN** a user edits type, status, project, time or authorized team-scope creator filters
- **THEN** the page waits for query submission rather than querying on each keystroke
- **AND** query submission or reset returns to page one, saves committed filters in URL state and applies identical predicates to list and summary
- **AND** project and creator labels use existing authorized bounded lookups or known values without preloading all projects or members

#### Scenario: Use the table toolbar and pagination
- **WHEN** a user refreshes or changes a page from the standard table controls
- **THEN** the page requests a server-filtered page with default size 20 and maximum 100, preserves the committed query and exposes only existing permitted task actions

#### Scenario: Open and close a responsive detail drawer
- **WHEN** a user opens a task from the view-detail action on desktop or a narrow screen
- **THEN** the right drawer uses a consistent title/action header, readable content sections and responsive media, remains within the viewport and confines wide content scrolling to its own region
- **AND** closing it restores list context without changing scope or permissions

## MODIFIED Requirements

### Requirement: Queries are paged and scoped consistently
The system SHALL provide server-side pagination, a default page size of 20 and maximum 100, status/type/project/time filtering, and creator filtering in team scope. Summaries SHALL use the same visibility and filters as the list. Ordering SHALL prioritize active work and use creation time and typed identity as deterministic tie breakers. Lists MUST NOT fetch full details for every row or all projects' task lists in the client. Lightweight list, summary and task-state responses MUST remain separate from authorized detail content. Only the selected task's requested content sections and child page SHALL be loaded, with bounded preview and continuation contracts.

#### Scenario: Filter across task types
- **WHEN** an authorized user filters failed tasks within a time range
- **THEN** the server returns a bounded page, matching total and summary computed from the same authorized query predicate

#### Scenario: Open one task detail
- **WHEN** a user selects one task
- **THEN** the client loads only the selected task's detail and requested child page, not every list row's complete payload
- **AND** authorized inputs and outcomes are obtained through bounded detail-content reads rather than added to the aggregate list query

#### Scenario: Poll a running task
- **WHEN** the visible task state is refreshed without a known content change
- **THEN** the system does not repeatedly fetch full prompts, scripts, result collections or media bodies

### Requirement: Center navigation and refresh preserve context
The center SHALL default to mine scope, preserve query and selected task in URL state, and show authorized result/business destinations. It SHALL refresh only active visible queries and stop polling on exit or terminal detail. A team switch SHALL discard previous-team rows and selection and ignore their late responses. Detail navigation SHALL preserve parent task, child-page position and selected section. Switching task, switching team or closing detail SHALL cancel stale content reads and release active media. Initial detail opening, explicit refresh and task completion SHALL revalidate relevant content; persisted content revision changes SHALL refresh only the open section when incremental outcomes are supported.

#### Scenario: Team changes during loading
- **WHEN** a user switches teams before an earlier response arrives
- **THEN** no previous-team rows, counts or details are rendered in the new context
- **AND** prior text and media content are cleared and late content responses are ignored

#### Scenario: Result destination is unavailable
- **WHEN** the domain resource is deleted or access is no longer granted
- **THEN** the center shows an unavailable destination and does not return an unauthorized media or business link

#### Scenario: A task completes while its detail is open
- **WHEN** the task transitions from an active state to a terminal state
- **THEN** the detail revalidates the available outcomes and stops its task-state polling without downloading unopened media

#### Scenario: Switch tasks while a result is loading
- **WHEN** a user selects a different task before a text or media request completes
- **THEN** the previous response cannot replace the new task's content and the new task loads only its own requested sections
