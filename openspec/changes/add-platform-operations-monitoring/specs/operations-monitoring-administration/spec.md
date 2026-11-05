## ADDED Requirements

### Requirement: Separate platform operations permissions
The system SHALL separately authorize metrics viewing, log viewing, rule editing, alert management and settings editing through platform permissions. Every backend endpoint and frontend action SHALL enforce its respective permission. Tenant roles and impersonation SHALL NOT inherit the originating administrator's cross-tenant privileges.

#### Scenario: Metrics-only operator requests log details
- **WHEN** a user with operations view permission but no operations log-view permission requests events, trace details or error distributions
- **THEN** the API denies the log access and the UI omits the corresponding detail actions

#### Scenario: Administrator impersonates a tenant member
- **WHEN** an impersonated tenant identity opens operations APIs without its own platform permissions
- **THEN** the system denies access rather than borrowing the real administrator's operations permissions

### Requirement: Redacted evidence and trusted attribution
The system SHALL redact and truncate monitoring input before enqueueing and SHALL use allowlisted response fields. It SHALL NOT collect bodies, prompts, scripts, Base64 media, authentication headers, cookie values, secret URLs or raw SQL parameters. Authenticated user attribution SHALL come from the session, and client-reported tenant/project IDs SHALL remain untrusted diagnostic hints.

#### Scenario: Error contains a provider credential
- **WHEN** an error message or stack includes a bearer token, API key or signed URL
- **THEN** the persisted monitoring evidence and its API response omit or redact the sensitive value

#### Scenario: Client submits a different tenant ID
- **WHEN** a frontend event supplies an arbitrary tenant identifier
- **THEN** the identifier is marked client-reported and is not treated as validated business ownership or used for authorization

### Requirement: Validated audited configuration
Rule and settings writes SHALL use validated allowlisted values and optimistic versions, and SHALL record actor, operation and redacted before/after evidence through the existing audit system. Notification secrets SHALL remain server-side configuration and SHALL never be returned by settings APIs. Test sends SHALL require an explicit authorized audited action.

#### Scenario: Concurrent settings edits
- **WHEN** two operators submit changes based on the same settings version
- **THEN** only the first update succeeds and the second receives a conflict response without overwriting the first change

#### Scenario: Settings response contains notification state
- **WHEN** an authorized operator reads monitoring settings
- **THEN** the response reports whether webhook/signature configuration is present without including either value

### Requirement: Bounded retention and cleanup
Default retention SHALL be seven days for minute metrics and resources, 90 days for hourly metrics and alert history/actions, 14 days for event details, 30 days for AI/execution projections and notification attempts. Configurable retention SHALL be between one and 90 days, hourly retention SHALL NOT be shorter than minute retention, and alert retention SHALL NOT be shorter than notification retention. Active unresolved alerts SHALL remain retained. Cleanup SHALL affect operations-owned tables only, at most 500 rows per transaction and 20 batches per run.

#### Scenario: Cleanup encounters business records
- **WHEN** operations cleanup runs after its retention horizon
- **THEN** it removes only eligible operations-owned rows and does not delete AI source logs, business tasks, assets, or point-accounting records

#### Scenario: Incident remains active past retention
- **WHEN** an unresolved alert is older than the configured alert retention
- **THEN** it remains visible with its lifecycle evidence until resolved and eligible for cleanup

### Requirement: Observable overhead and health
The module SHALL expose capture queue depth, discarded detail count, dimension overflow, database timeouts, reconciliation delay, last aggregate window, evaluator heartbeat and notification backlog. The monitoring pool SHALL have at most two database connections and SHALL not use business pool connections on exhaustion.

#### Scenario: Monitor data is stale
- **WHEN** the latest successful aggregate or collector heartbeat is outside its expected interval
- **THEN** operators see the stale timestamp and coverage warning instead of an apparently healthy live value

### Requirement: Measured release acceptance and rollback
Release validation SHALL compare identical controlled workloads with monitoring disabled and enabled. For the defined fixture, HTTP P95 increase SHALL be at most five percent and at most 20 milliseconds, aggregate dashboard query P95 SHALL be at most one second, and queue/database failure injection SHALL preserve business response behavior. Rollback SHALL retain the new operations tables and data and restore exactly one existing hourly-digest sender.

#### Scenario: Performance budget is exceeded
- **WHEN** controlled validation exceeds either HTTP overhead budget
- **THEN** the release is not accepted until telemetry frequency, cardinality or storage work is reduced and the controlled check passes

#### Scenario: Application version is rolled back
- **WHEN** a deployment is reverted to the previous application version
- **THEN** retained operations data is not dropped and digest ownership is restored without enabling duplicate new and old senders
