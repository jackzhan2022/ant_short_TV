# production-task-center Specification

## Purpose
Define a unified center for discovering, tracking and controlling authorized durable asynchronous production tasks while preserving domain identities, results and lifecycle rules.
## Requirements
### Requirement: Production tasks cover durable asynchronous business work
The system SHALL aggregate existing durable asynchronous production work irrespective of AI usage, including video decomposition, script analysis and script AI operations, review, storyboard generation, image/video/voice generation, shot composition, and episode composition where those workflows execute asynchronously. Integration SHALL document each source's actual execution model and coverage. Synchronous operations and missing asynchronous implementations MUST NOT be represented as recoverable background jobs or silently counted as integrated asynchronous types.

#### Scenario: Observe a non-AI composition task
- **WHEN** an authorized user queries a persisted asynchronous composition task without an AI execution identifier
- **THEN** the center returns its business status, available progress and result metadata without requiring an AI execution

#### Scenario: Source has only synchronous execution
- **WHEN** source inspection finds a named production task executes synchronously
- **THEN** the integration records the coverage gap and does not fabricate queued, recoverable or cancelable background behavior

#### Scenario: Reopen on another device
- **WHEN** a user opens the center without prior browser storage
- **THEN** authorized persisted tasks are discoverable from the server

### Requirement: Top-level identity represents business work
The system SHALL use stable typed business identifiers. Existing persisted submission batches SHALL appear once at the top level with their members under a paged child view. Technical retries, provider polls and internal Agent calls MUST NOT create top-level duplicates. Independent submissions without a reliable parent relation SHALL remain independent tasks.

#### Scenario: Batch contains ten episode executions
- **WHEN** a persisted ten-episode decomposition batch is listed
- **THEN** one batch row appears and its ten children are accessible through the authorized child view
- **AND** internal attempts do not increase the top-level total

#### Scenario: Retry versus regeneration
- **WHEN** a failed business task undergoes a technical retry
- **THEN** its business identity remains stable
- **AND** intentional regeneration preserves prior results and follows the domain's new task or execution-version lineage

### Requirement: Queries are paged and scoped consistently
The system SHALL provide server-side pagination, a default page size of 20 and maximum 100, status/type/project/time filtering, and creator filtering in team scope. Summaries SHALL use the same visibility and filters as the list. Ordering SHALL prioritize active work and use creation time and typed identity as deterministic tie breakers. Lists MUST NOT fetch full details for every row or all projects' task lists in the client.

#### Scenario: Filter across task types
- **WHEN** an authorized user filters failed tasks within a time range
- **THEN** the server returns a bounded page, matching total and summary computed from the same authorized query predicate

#### Scenario: Open one task detail
- **WHEN** a user selects one task
- **THEN** the client loads only the selected task's detail and requested child page, not every list row's complete payload

### Requirement: Progress preserves business meaning
The system SHALL expose QUEUED, RUNNING, WAITING_USER, SUCCEEDED, PARTIAL, FAILED and CANCELED groups with the original domain status. WAITING_USER SHALL require a real user-dependent workflow gate. Unsupported numeric progress SHALL be null and represented by phase or item counts. Timeout SHALL map to FAILED and successful results with warnings SHALL remain SUCCEEDED with separate warning metadata.

#### Scenario: Batch finishes with mixed outcomes
- **WHEN** all children are terminal with at least one success and at least one failure or cancellation
- **THEN** the batch is PARTIAL and its mutually exclusive child counts sum to its total
- **AND** existing successful results remain discoverable

#### Scenario: Review report contains findings
- **WHEN** review generation succeeds and the report contains issues
- **THEN** the task is SUCCEEDED rather than automatically WAITING_USER

#### Scenario: Provider supplies no numeric progress
- **WHEN** a running task exposes only a phase
- **THEN** the center displays the phase without inventing a percentage

### Requirement: Own-task controls preserve domain rules
The system SHALL calculate allowedActions on the server and revalidate ownership, current domain permissions, lifecycle and supported handler behavior at mutation time. The center SHALL permit only own-task cancellation, retry or regeneration where the source supports them. It SHALL preserve domain idempotency, billing, auditing and result lineage, and SHALL not offer global create, delete or archive actions in this version.

#### Scenario: Stale retry action
- **WHEN** a user retries a task that became ineligible after the list was loaded
- **THEN** the server rejects the transition without creating a new billable execution

#### Scenario: Unsupported batch action
- **WHEN** the source has no reliable batch-level cancellation or retry
- **THEN** the center omits that batch action and exposes only supported authorized child actions

### Requirement: Center navigation and refresh preserve context
The center SHALL default to mine scope, preserve query and selected task in URL state, and show authorized result/business destinations. It SHALL refresh only active visible queries and stop polling on exit or terminal detail. A team switch SHALL discard previous-team rows and selection and ignore their late responses.

#### Scenario: Team changes during loading
- **WHEN** a user switches teams before an earlier response arrives
- **THEN** no previous-team rows, counts or details are rendered in the new context

#### Scenario: Result destination is unavailable
- **WHEN** the domain resource is deleted or access is no longer granted
- **THEN** the center shows an unavailable destination and does not return an unauthorized media or business link
