## Context

The platform has a useful but incomplete foundation: provider/model/project configuration, `AiInvocationService`, `ai_call_log`, team point accounts, and reliable claim/attempt metadata for selected video workflows. Execution remains fragmented. Script generation and rewrite block HTTP requests, image execution relies on post-commit `@Async`, script analysis and review use scheduler scans, provider-native video jobs are submitted and polled directly by a domain service, and some callers bypass the unified invocation boundary.

Accounting is similarly split. Business services usually consume one point before a call, many point transactions have no business identifier, call logs are not linked to point transactions, and `estimated_cost` is always zero. The data model has token columns but cannot express composite pricing such as input and output tokens, per-call plus per-image, or per-second pricing by resolution.

The design must preserve domain-specific task and result records, work with the existing Spring Boot/MySQL/MyBatis/JdbcTemplate stack, avoid adding an operational message-broker dependency, and permit incremental migration. Existing `ai-task-execution-reliability` and unified invocation behavior are foundations rather than replacements.

## Goals / Non-Goals

**Goals:**

- Give every user-facing AI operation one durable, observable asynchronous execution contract.
- Make provider calls idempotent and recoverable across process failure.
- Correlate domain work, attempts, call logs, usage, provider cost, and point settlement.
- Support synchronous and provider-native asynchronous adapters behind one worker contract.
- Support composite per-call, per-image, per-token, per-second, and per-character pricing.
- Keep provider currency cost independent from customer point pricing.
- Migrate current workflows without discarding their domain tables or historical records.

**Non-Goals:**

- Do not replace existing script, review, image, video, shot, or material result models with a generic AI-result blob.
- Do not introduce Kafka, RabbitMQ, or another broker in this change.
- Do not implement subscription plans, invoices, payment collection, tax, or currency conversion.
- Do not implement provider invoice ingestion; cost is calculated from captured usage and configured price versions.
- Do not make prompt-template or Agent catalog management editable.
- Provider connectivity tests and prompt previews may remain synchronous because they are administrative diagnostics rather than user production jobs.

## Decisions

### 1. Add a shared execution header while retaining domain ownership

Introduce `ai_execution_task` as the orchestration record and retain current domain task/result tables. Each execution links to `business_type` and `business_id`; a registered handler resolves domain input, performs phases, and persists domain output. Script generate/rewrite operations that currently have no pre-call task record will create an execution-linked operation record or store a resumable, redacted input payload on the execution.

Core task fields include tenant/user/project, scene, capability, domain link, requested and resolved model, canonical status, phase, progress, execution version, client idempotency key, trace id, claim token and timeout, retryability, result link, error details, settlement summary, and timestamps. A unique key on tenant, business scene, and client idempotency key prevents duplicate creation.

`ai_execution_attempt` records phase, attempt number, execution version, idempotency key, whether provider contact occurred, provider request/external task IDs, call-log linkage, retryability, errors, and timestamps. A unique execution/phase/version/idempotency constraint is authoritative.

Alternative considered: replace all domain task tables with a generic task and JSON result. Rejected because domain workflows have materially different validation, progress, review, material, and result-selection behavior.

For script migration, use a hybrid ownership model. Provider-facing script operations that do not already have a durable task (`SCRIPT_GENERATE`, `SCRIPT_REWRITE`, element extraction, storyboard breakdown, and prompt generation) create a `script_ai_operation` record before execution. The operation stores only resumable redacted input, idempotency/state, execution correlation, and typed result references; generated script versions, assets, and storyboards remain in their existing domain tables. Existing complex domain tasks (`script_analysis_task` and `review_task`) remain authoritative for stage and issue-round state and link directly to the shared execution header instead of being copied into the generic operation table.

Initial script analysis triggered as an enhancement after project creation does not reserve points in the project-creation transaction. It first persists the domain analysis task so project creation remains independent of point balance; before the first provider call, the dispatcher atomically creates the shared execution and its reservation. If reservation fails, the domain task remains pending with an actionable insufficient-points state and can be resumed later. Explicit user reanalysis creates the domain task, shared execution, and reservation at submission time and returns the shared asynchronous task response. Analysis point policy is per provider call: reserve the request-derived maximum call count, settle from actual calls, and release unused reservation.

### 2. Use database-backed dispatch with atomic claims

Task creation and point reservation commit in one transaction. Scheduled dispatchers query eligible tasks in bounded batches and claim them with conditional updates. An immediate post-commit wake-up may reduce latency, but correctness relies on the persisted `PENDING` row and periodic scan. Claim expiry makes abandoned work retryable according to scene policy.

This is an inbox-style database queue using existing infrastructure. It removes the image workflow's crash window without adding a broker. Batch size, poll interval, claim timeout, and tenant concurrency are configuration properties.

Alternative considered: use Spring `@Async` only. Rejected because executor submission is not durable. A message broker offers higher scale but adds infrastructure and dual-write concerns that are unnecessary at current scale.

### 3. Separate database transactions from network operations

Workers use short phases:

1. transactionally claim the task and create a running attempt;
2. commit;
3. perform provider submission or synchronous invocation outside a database transaction;
4. persist call log, usage, and attempt outcome in short transactions;
5. transactionally finalize domain state, task state, and point settlement.

No database transaction remains open while waiting for a provider. `AiCallLogWriter` continues to preserve evidence of real external calls independently. If domain finalization fails after provider success, the persisted call and usage remain available for recovery and settlement policy.

Alternative considered: wrap the entire worker in one transaction. Rejected because it holds database resources during slow network calls and can roll back point/task evidence after the provider has already charged the platform.

### 4. Add execution handlers above the unified invocation boundary

An `AiExecutionHandler` registry maps stable business scenes to validation, execution phases, domain input/output, retry policy, and usage expectations. Handlers call `AiInvocationService`; domain services no longer select adapters, perform provider HTTP, or manually search/write call logs.

Provider adapters expose one of two outcomes:

- completed: final provider response and usage are available;
- accepted: an external job ID and next-poll information are available.

An accepted attempt moves the execution to a polling phase. Poll operations are claimed and attempted idempotently like submissions. Provider transport remains adapter-owned; business parsing remains handler-owned so `markBusinessFailure` can preserve transport evidence.

Alternative considered: make `AiInvocationService` itself own every domain task. Rejected because it would mix transport, orchestration, and domain result persistence.

### 5. Standardize public task APIs and compatibility migration

The shared response contains execution ID, domain link, canonical status, phase, progress, retryability, normalized error, result references, usage-cost status, point-settlement status, and timestamps. Shared endpoints provide task detail, cancel, and retry. Domain create endpoints return HTTP 202 with this task envelope.

Frontend pages migrate one workflow at a time to polling the shared task detail; SSE can be added later without changing task persistence. During migration, old synchronous endpoint behavior may be retained behind explicitly versioned compatibility routes, but new production AI operations must use the task contract. Final removal of compatibility routes is a documented breaking API step followed by generated-client regeneration rather than manual edits.

Alternative considered: keep mixed synchronous and asynchronous public behavior. Rejected because it perpetuates different timeout, retry, error, and settlement semantics.

### 6. Store usage as immutable metric lines

Add `ai_usage_line` linked to execution, attempt, and call log. Each line contains metric, quantity as `decimal(24,8)`, unit, source (`PROVIDER_REPORTED`, `REQUEST_DERIVED`, `RESULT_MEASURED`, or `ADJUSTMENT`), dimensions JSON, observed time, and correction linkage. Summary token columns may remain as denormalized compatibility fields but are not the accounting source of truth.

Adapters return normalized usage facts. Handlers add result-derived facts such as generated image count or successfully generated media duration. A call can produce multiple lines.

Alternative considered: add image count and duration columns to `ai_call_log`. Rejected because each new provider metric would require another schema change and composite meters remain awkward.

### 7. Version model prices and snapshot cost lines

Add immutable `ai_model_price_version` and `ai_model_price_component`. Components contain metric, unit size, unit price, currency, matching dimensions, effective interval, and status. Publishing a new version closes or supersedes the prior effective interval; settled historical versions are never edited.

Pricing selects the model and effective version at provider-contact time, then matches each usage line by metric and dimensions. The formula is `quantity / unit_size * unit_price`, using `BigDecimal` and configured calculation scale. `ai_usage_cost_line` snapshots component ID/version, quantity, unit size, unit price, currency, raw calculated cost, rounded cost, and pricing status. Invocation and task totals are derived summaries grouped by currency.

Missing usage or price yields `INCOMPLETE` or `UNPRICED`, never zero. Corrections create adjustment usage and cost lines. No implicit exchange-rate conversion is performed.

Alternative considered: store one billing mode and price on `ai_model`. Rejected because real models use composite and dimensional pricing.

### 8. Model point pricing separately from provider cost

Add versioned point policies and components keyed by business scene with optional model/capability/dimension matching. Components can be fixed per execution or use the same normalized metrics with a point unit rate. The policy version is captured when the execution is created.

Provider cost answers what the platform spent in currency. Point settlement answers what the tenant is charged under product policy. Neither is derived implicitly from the other, though a future policy type may deliberately reference cost.

Alternative considered: convert currency cost directly to points. Rejected because product price, promotions, fixed scene charges, and margins must remain independent.

### 9. Reserve and settle points with an append-only ledger

Extend the point account to distinguish available, reserved, granted, consumed, and refunded totals. Add a reservation/settlement identity per execution version and append-only ledger entries for `RESERVE`, `SETTLE`, `RELEASE`, `REFUND`, `GRANT`, and `ADJUST`.

Reservation computes a conservative maximum from request limits and the captured policy. The account update and reservation ledger entry are atomic and idempotent. Final settlement consumes the actual policy amount and releases unused reservation. If unexpected usage exceeds reservation, the system attempts an atomic incremental reserve; insufficient balance produces `SETTLEMENT_REVIEW_REQUIRED` rather than a silent negative balance or data loss.

Scene policy determines whether provider-billed failure, timeout, or business parsing failure is charged. Cancellation before provider contact always releases the full reservation. Infrastructure retries within one execution version reuse the reservation; intentional regeneration creates a new version and reservation.

For domain workflows whose result ownership is task-scoped, including image generation, intentional regeneration creates a new domain task for the new result set. The new execution records both the immediately preceding source execution and the stable root execution, receives the next version within that lineage, and owns an independent reservation and settlement. Prior domain tasks, results, executions, and charges remain unchanged and queryable.

Alternative considered: deduct immediately and insert refunds later. Rejected because cancellation and pre-call failures become difficult to distinguish from consumed service and concurrent retries can double-deduct.

### 10. Make correlation and observability first-class

Execution ID and trace ID flow through task, attempt, invocation request, call log, usage, cost, domain result, and point ledger. Call-log status uses canonical transport/business outcome fields rather than custom values such as `QUERY_RUNNING` in the primary status. Provider polling events may be stored as attempts or structured events without pretending each poll is a generated-result charge.

Prompt and response summaries use scene-specific redaction and length limits. APIs never expose API-key ciphertext, raw secrets, or unrestricted prompts. Operational queries include unpriced usage, settlement review, expired claims, retry exhaustion, provider failure rate, and cost/point reconciliation.

Alternative considered: infer links by tenant, user, scene, and time. Rejected because concurrent calls make heuristic linkage incorrect.

## Risks / Trade-offs

- [The change spans many workflows] -> Introduce shared tables and APIs first, then migrate one scene at a time behind compatibility routes with targeted contract tests.
- [Database polling adds load] -> Use indexed eligibility queries, bounded batches, configurable delays, claim timeouts, and tenant concurrency limits.
- [A worker crashes after provider contact but before recording the response] -> Persist attempt and idempotency key before contact, send provider idempotency keys where supported, and expose reconciliation state where the provider cannot guarantee idempotency.
- [Usage reported by providers is incomplete] -> Distinguish provider-reported, request-derived, and result-measured usage and mark cost incomplete rather than fabricating values.
- [Dimensional price matching becomes ambiguous] -> Validate that active price components do not overlap for the same model, metric, dimensions, and effective interval.
- [Reservation cannot perfectly bound variable output] -> Use request maximums where available and route unexpected overage to explicit settlement review.
- [Task conversion changes frontend behavior] -> Provide a shared polling client and migrate routes/pages together; regenerate OpenAPI clients after controller contracts change.
- [Historical fixed point transactions lack correlations] -> Treat them as legacy settled entries and begin strict correlation at the migration cutover instead of inventing links.

## Migration Plan

1. Add execution, attempt, usage, pricing, cost, reservation, and ledger schema additively; seed fixed one-point policies that preserve current commercial behavior.
2. Implement execution repositories, atomic claim/timeout recovery, database dispatcher, handler registry, usage normalization, price resolution, cost calculation, and point reservation/settlement services.
3. Add shared task APIs and frontend polling support while keeping existing domain read/result APIs.
4. Migrate AI image first because it has non-durable dispatch and heuristic log linking; route it through `AiInvocationService` and the shared attempt model.
5. Migrate script generate/rewrite/extraction, script analysis, and review; convert synchronous endpoints to task responses after frontend consumers are ready.
6. Migrate video submission/polling from direct HTTP/manual logging into adapter and execution phases, preserving existing video result records.
7. Migrate remaining shot audio/compose AI operations that contact providers, then prevent new business modules from using legacy gateways or direct provider HTTP.
8. Enable structured model pricing and cost reporting; keep calls explicitly `UNPRICED` until prices are published.
9. Enable non-fixed point policies after reconciliation verifies task, usage, cost, and ledger links under seeded compatibility policies.
10. Remove compatibility endpoints and legacy AI configuration/call paths in a separately reviewable cleanup after all callers have migrated.

Deployment is additive until each workflow cutover. Rollback disables the migrated handler/route and resumes the prior workflow while retaining new execution/accounting records for audit. Schema rollback does not delete accounting records; unused new tables can remain dormant.

## Open Questions

- Streaming conversational AI is not currently a primary workflow. When introduced, it should create an execution before streaming and append usage as the stream closes, but transport-specific streaming APIs are deferred.
- Provider invoice reconciliation and foreign-exchange reporting are deferred; costs remain grouped by original currency.
