## ADDED Requirements

### Requirement: Configurable evidence-based alert rules
Rules SHALL support metric, scope, severity, operator, threshold, window, minimum samples, sustained duration, recovery conditions, cooldown, and notification policy. Rules SHALL distinguish technical errors from expected business rejection, active runnable queueing from future retries, and unfinished settlement from legitimately inapplicable settlement.

#### Scenario: One failure in a low-volume period
- **WHEN** a rate rule requires at least 100 requests but observes only one failed request
- **THEN** the evaluator records insufficient samples without firing a percentage-based outage alert

#### Scenario: Task waits for a future retry time
- **WHEN** a pending task has a future next_run_at
- **THEN** it is counted as retry backoff and does not increase oldest runnable queue age

### Requirement: Persisted sustained-window state
The evaluator SHALL persist NORMAL, PENDING, FIRING and RESOLVED transitions per rule and bounded stable dimension. It SHALL evaluate closed minute windows, avoid advancing sustained counters more than once for the same window, and interrupt sustained evidence across missing windows. Recovery SHALL require two valid healthy windows by default. Missing data SHALL NOT prove recovery.

#### Scenario: Same window evaluated twice
- **WHEN** a 30-second evaluator processes the same closed minute window twice
- **THEN** it advances sustained evidence once and cannot fire earlier merely because evaluation repeated

#### Scenario: Evaluator restarts during an incident
- **WHEN** an alert evaluator restarts after an event has fired
- **THEN** it resumes the persisted active event instead of opening a duplicate incident or forgetting its cooldown

#### Scenario: Telemetry stops during an incident
- **WHEN** an active alert has no new valid observations
- **THEN** the event remains unresolved and displays unknown/stale evidence until healthy observations establish recovery

### Requirement: Acknowledgement silence and recovery lifecycle
Operators with alert management permission SHALL acknowledge incidents and create expiring silences with reasons. Acknowledgement and silence SHALL NOT imply recovery. Continuing breaches SHALL update the existing incident, reminder messages SHALL respect cooldown and per-rule limits, and recovery SHALL retain the original trigger evidence.

#### Scenario: Operator acknowledges an active fault
- **WHEN** an operator acknowledges an active event
- **THEN** the event records actor and time while remaining FIRING until its actual recovery conditions are met

#### Scenario: Silence expires while fault continues
- **WHEN** an expiring silence ends and the fault remains active
- **THEN** notification eligibility resumes subject to the persisted cooldown without creating a new duplicate incident

### Requirement: Persistent notification delivery
Alert state transitions and their notification outbox entries SHALL commit atomically. Workers SHALL use bounded claims and expiring leases, record attempts, and require HTTP success plus Feishu business code zero before marking delivery successful. Transient failures SHALL follow retry delays of 1, 5 and then 15 minutes with no more than five attempts. Exhausted failures SHALL remain visible.

#### Scenario: Notification worker crashes after claim
- **WHEN** a worker claims a pending notification and stops before recording its result
- **THEN** another worker can reclaim it after lease expiry without creating another outbox row for the same event transition and channel

#### Scenario: Feishu returns an application error with HTTP 200
- **WHEN** Feishu returns HTTP 200 with a nonzero business code
- **THEN** the attempt is marked failed and retried according to policy

#### Scenario: Delivery response is lost
- **WHEN** a send may have succeeded remotely but its response is lost
- **THEN** retries carry the same stable event and transition identifiers and the system does not claim exactly-once remote delivery

### Requirement: Multi-instance coordination and observer safety
Cluster-wide evaluation, aggregation, cleanup, notifications and the hourly summary SHALL coordinate using persisted leases with ownership versions and idempotency keys. Slow or stale owners SHALL NOT publish conflicting state transitions after losing ownership. Network sends SHALL execute outside database transactions.

#### Scenario: Two instances evaluate the same active rule
- **WHEN** two application instances concurrently attempt the same rule/window evaluation
- **THEN** ownership and uniqueness constraints allow one effective state transition and one outbox entry

### Requirement: Observation mode and existing digest compatibility
New immediate alert delivery SHALL default to a 24-hour observation period. Existing enabled hourly digests SHALL retain their Asia/Shanghai half-hour schedule while using one persisted event source and one coordinated sender in the new mode. Technical telemetry and dashboard availability SHALL NOT depend on Feishu being configured.

#### Scenario: New deployment has no webhook
- **WHEN** telemetry is enabled and no notification channel is configured
- **THEN** metrics, histories and alert evaluation operate and the UI reports the unavailable notification channel without pretending delivery occurred

### Requirement: Independent whole-service availability monitoring
The delivery SHALL include an external availability-probe configuration contract for execution outside the application host, with a 30-second interval, three consecutive failures before firing and two successes before recovery. The dashboard SHALL distinguish configured external coverage from application-only monitoring. The public health response SHALL reveal no credentials or internal connection details.

#### Scenario: Entire application host stops
- **WHEN** an independently hosted configured probe repeatedly cannot reach the application health endpoint
- **THEN** that external probe can issue the availability notification without requiring the unavailable application or its database

#### Scenario: External probe has not been deployed
- **WHEN** no external coverage has been registered
- **THEN** the dashboard explicitly reports that whole-host outage notifications are not configured
