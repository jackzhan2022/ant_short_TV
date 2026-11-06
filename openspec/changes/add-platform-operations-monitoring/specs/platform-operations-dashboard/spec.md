## ADDED Requirements

### Requirement: Platform operations workspace
The system SHALL provide a platform operations workspace with overview, request performance, AI operations, background jobs, error events, alerts, and monitoring settings. The workspace SHALL enforce platform operations access and SHALL NOT grant access solely because a user administers a tenant.

#### Scenario: Authorized platform operator
- **WHEN** a user with `PLATFORM_OPERATIONS_VIEW` opens `/operations`
- **THEN** the workspace displays the authorized panels and loads the last one hour by default

#### Scenario: Tenant administrator lacks platform permission
- **WHEN** a tenant administrator without platform operations permissions accesses the route or its APIs
- **THEN** the system denies access without exposing platform metrics or cross-tenant records

### Requirement: Explicit metric scope and meaning
The system SHALL distinguish HTTP requests, AI calls, execution versions, execution attempts, cancellations, business rejections, and technical failures. Metrics SHALL include their unit, scope, sampling coverage, time bounds, and freshness. Unsupported dimensions SHALL NOT silently filter unrelated system metrics.

#### Scenario: An AI call succeeds but business parsing fails
- **WHEN** a provider response succeeds at transport level and fails business parsing
- **THEN** the AI panel distinguishes transport success and business failure without treating them as two independent provider requests

#### Scenario: No observations exist
- **WHEN** a requested period contains no valid latency or rate samples
- **THEN** the corresponding metric displays no data and does not fabricate zero latency, full success, TTFT, or a healthy score

### Requirement: Bounded historical queries
The system SHALL support preset and custom time ranges up to 90 days, use minute or hour aggregates according to retention and requested resolution, and paginate detail results with time and ID cursors. Page size SHALL default to 20 and SHALL NOT exceed 100. Display times SHALL use Asia/Shanghai and API timestamps SHALL carry UTC offsets.

#### Scenario: Long historical range
- **WHEN** an operator requests a 30-day overview
- **THEN** the system uses retained hourly aggregates and identifies the data resolution without scanning business JSON or individual historical HTTP requests

#### Scenario: Detail data expired
- **WHEN** an operator opens a historical aggregate whose call or event detail has passed its retention period
- **THEN** the aggregate remains visible and the detail panel explicitly reports that retained detail is unavailable

### Requirement: Coordinated refresh and stale response protection
The workspace SHALL refresh only active live panels on a shared cadence, with a default 15-second delay measured after completion. It SHALL pause while hidden, refresh on return, cancel obsolete requests on filter or scope changes, and ignore late responses. Fixed historical ranges SHALL remain static until explicitly refreshed.

#### Scenario: Request exceeds refresh interval
- **WHEN** an overview request takes more than 15 seconds
- **THEN** no second timer-driven request overlaps it and the next refresh starts only after the completed request plus the refresh delay

#### Scenario: Filters change while a request is running
- **WHEN** an operator changes tenant or time filters before an earlier response returns
- **THEN** the obsolete request is cancelled where possible and its response cannot update the new scope

### Requirement: Evidence-based troubleshooting
The workspace SHALL provide error fingerprints, sanitized details, request/trace/execution references, slow-request SQL count and cumulative time where collected, job heartbeats, queue age, lease status, and alert evidence. Heavy detail SHALL be loaded only on demand. Existing business links SHALL retain their original authorization requirements.

#### Scenario: A workflow request is slow
- **WHEN** an observed request exceeds its slow threshold and has SQL observations
- **THEN** the detail shows HTTP duration, SQL count, cumulative SQL duration, normalized route, and request ID without presenting database time as proof of a missing index

#### Scenario: A panel source fails
- **WHEN** one source times out while other panel sources succeed
- **THEN** the workspace retains successful panels and the last successful data for the failed panel with an explicit stale/error indicator
