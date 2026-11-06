## ADDED Requirements

### Requirement: Nonblocking bounded capture
Telemetry capture SHALL use bounded counters and a nonblocking queue outside business persistence transactions. Default queue capacity SHALL be 5000, detail flush batches SHALL contain at most 100 rows and run at most once per second, and failures SHALL NOT change the business result. Queue drops, dimension overflow, and freshness gaps SHALL be observable.

#### Scenario: Monitoring queue is full
- **WHEN** a business request emits telemetry while the queue is full
- **THEN** the business request continues with its original response and a monitoring drop counter increments

#### Scenario: Monitoring database is unavailable
- **WHEN** persistence cannot acquire its independent monitoring connection within one second
- **THEN** the monitoring writer backs off within bounded memory without borrowing business pool connections or propagating the exception to business requests

### Requirement: Normalized request and database observations
The system SHALL collect normalized route, method, result category, duration, request ID and available synchronous SQL count/time without recording request bodies, response bodies, credentials, query values, or raw SQL parameters. Route dimensions SHALL be bounded to 2000 combinations per instance per minute with overflow grouped into OTHER. Monitoring and health routes SHALL be excluded from business metrics.

#### Scenario: Many dynamic paths
- **WHEN** requests address many distinct project IDs through the same route
- **THEN** they aggregate under the matched route template instead of creating one metric series per project ID

#### Scenario: Nested database instrumentation
- **WHEN** an observed database call passes through nested timing boundaries
- **THEN** the query is counted once and the enclosing request observation is restored after the nested scope closes

### Requirement: Mergeable latency histograms
Minute and hourly metric buckets SHALL retain counts, sums, maxima and mergeable latency histogram counts. Percentiles SHALL be derived from merged counts, SHALL be marked approximate, and SHALL NOT be averaged across instance or time-window percentiles. Zero observations SHALL produce absent percentiles.

#### Scenario: Different traffic volume across instances
- **WHEN** one instance observes one slow request and another observes many fast requests
- **THEN** global percentiles use the combined request distribution and not an equal average of the two instance percentiles

#### Scenario: Latency exceeds the largest finite bucket
- **WHEN** a percentile falls in the bucket above 1800000 milliseconds
- **THEN** the system reports it as exceeding that bound rather than returning a fabricated finite duration

### Requirement: Persistent sanitized events and execution history
The system SHALL persist sanitized operational events with stable deduplication keys and SHALL project lightweight AI call and execution-version observations. Distinct execution versions SHALL NOT overwrite already captured failure history. Source result revisions SHALL update their call projection and affected aggregate buckets idempotently. Unrecoverable or incomplete source history SHALL be identified as partial.

#### Scenario: Failed execution is retried
- **WHEN** a captured failed execution advances to a new execution version
- **THEN** the old observed version remains queryable and the new version contributes independently to task counts

#### Scenario: AI business outcome is corrected after insertion
- **WHEN** an existing call log changes from business success to business failure
- **THEN** its projection and affected minute/hour counts are recomputed without double-counting the call ID

#### Scenario: Source history cannot reconstruct a missed version
- **WHEN** an unflushed version snapshot is lost and the business source no longer preserves that version
- **THEN** the system marks the affected historical coverage partial instead of claiming complete recovery

### Requirement: Resource and job health observations
The system SHALL sample resource observations at most every 15 seconds by default and identify whether values describe the JVM, container, or host. It SHALL record actual enabled jobs, schedule type, expected next start, start/end/success timestamps, execution duration and sanitized failure evidence. Monitoring health checks SHALL execute independently of the shared business scheduler.

#### Scenario: Redis is not configured
- **WHEN** Redis-backed query caching is disabled or unconfigured
- **THEN** Redis health displays NOT_CONFIGURED and does not generate a Redis outage

#### Scenario: Fixed-delay job is still executing
- **WHEN** a fixed-delay job has not finished
- **THEN** the system evaluates its execution duration separately and does not incorrectly schedule its next start relative to its previous start time

#### Scenario: Shared business scheduler stalls
- **WHEN** registered business jobs stop starting on schedule
- **THEN** the independent observer can record stale starts and provide evidence to the alert evaluator

### Requirement: Bounded source reconciliation
Reconciliation SHALL use lightweight indexed source fields, committed cursors, idempotent upserts, overlapping recent windows and bounded recent-history sweeps. Each run SHALL process at most 500 source records, recover queryable recent data within the 24-hour reconciliation horizon, and SHALL NOT perform an automatic unbounded startup backfill.

#### Scenario: A transaction commits later than its source timestamp
- **WHEN** a source record becomes visible after an earlier scan advanced its high-water mark
- **THEN** the recent overlap or bounded recent-history sweep can reconcile it without assuming timestamps or IDs establish commit order
