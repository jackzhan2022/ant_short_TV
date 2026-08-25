## 1. Schema and Compatibility Baseline

- [ ] 1.1 Add failing migration and mapper tests for execution-task, attempt, correlation, and uniqueness requirements.
- [ ] 1.2 Add an additive migration for `ai_execution_task` with canonical lifecycle, domain links, idempotency, claim, progress, error, and settlement summary fields plus eligibility indexes.
- [ ] 1.3 Add an additive migration for `ai_execution_attempt` with execution version, phase, provider-contact evidence, provider identifiers, call-log link, retry data, and uniqueness constraints.
- [ ] 1.4 Add execution and attempt entities, mappers/repositories, canonical status types, and response mappings.
- [ ] 1.5 Add execution/attempt foreign correlation columns to `ai_call_log` and relevant domain task/result tables without rewriting historical rows.
- [ ] 1.6 Seed compatibility metadata required to preserve current fixed one-point behavior during workflow migration.

## 2. Durable Execution Core

- [ ] 2.1 Add failing tests for idempotent task creation, atomic claims, claim loss, timeout recovery, and regeneration execution versions.
- [ ] 2.2 Implement transactional execution creation with tenant, scene, domain resource, model request, client idempotency key, trace ID, and initial `PENDING` state.
- [ ] 2.3 Implement conditional atomic claim, claim heartbeat/expiry, tenant concurrency enforcement, and attempt creation in short transactions.
- [ ] 2.4 Implement the database-backed dispatcher with bounded indexed scans and configurable batch, delay, timeout, and concurrency settings.
- [ ] 2.5 Implement expired-claim recovery and normalized `TIMED_OUT` or retryable outcomes without leaving executions permanently running.
- [ ] 2.6 Add the `AiExecutionHandler` contract and scene registry for validation, phases, domain input/result links, retry policy, and usage expectations.
- [ ] 2.7 Implement worker orchestration so claim and finalization use short transactions while all provider network operations run outside database transactions.
- [ ] 2.8 Add task cancellation, retry, and regeneration services with strict state validation and execution-version semantics.

## 3. Unified Provider Execution

- [ ] 3.1 Add contract tests for completed synchronous provider outcomes and accepted provider-native asynchronous outcomes.
- [ ] 3.2 Extend the invocation/adapter result contract to represent final completion or accepted external work without exposing provider differences to domain services.
- [ ] 3.3 Propagate execution ID, attempt ID, phase, execution version, idempotency key, and trace ID through `AiInvocationRequest`, results, errors, and call logs.
- [ ] 3.4 Implement idempotent provider submission and polling phases, including stable provider keys where supported and explicit reconciliation status where unsupported.
- [ ] 3.5 Standardize transport outcome, business outcome, normalized errors, provider request ID, external task ID, and business-parsing failure updates.
- [ ] 3.6 Add architecture tests or dependency checks preventing migrated handlers from direct provider HTTP, manual call-log inserts, or heuristic latest-log lookup.

## 4. Usage and Provider Cost Accounting

- [ ] 4.1 Add failing tests for token, image, video-second, per-call, composite, dimensional, missing-price, and correction scenarios.
- [ ] 4.2 Add migrations and persistence models for immutable `ai_usage_line` records and adjustment linkage.
- [ ] 4.3 Add migrations and persistence models for effective-dated model price versions and metric/dimension price components, including overlap constraints or validation.
- [ ] 4.4 Add migrations and persistence models for immutable usage-cost snapshots with currency and `PRICED`, `UNPRICED`, or `INCOMPLETE` status.
- [ ] 4.5 Implement normalized usage extraction for provider-reported tokens and request-derived or result-measured calls, images, characters, and media duration.
- [ ] 4.6 Implement effective price resolution by model, metric, dimensions, and provider-contact time.
- [ ] 4.7 Implement `BigDecimal` cost calculation and immutable component snapshots without silently substituting zero for missing prices.
- [ ] 4.8 Implement adjustment lines, task/call cost summaries grouped by currency, and reconciliation queries from aggregate to source usage.

## 5. Point Reservation and Settlement

- [ ] 5.1 Add failing concurrency and idempotency tests for reserve, insufficient balance, settle, release, refund, incremental reserve, and reconciliation.
- [ ] 5.2 Add migrations for reserved account balance/totals, versioned point policies and components, execution reservations, and append-only ledger correlations.
- [ ] 5.3 Implement versioned point-policy resolution supporting fixed scene, per-call, per-image, per-token, per-second, per-character, and composite rules independently of provider cost.
- [ ] 5.4 Implement atomic idempotent reservation in the same transaction as execution creation and reject insufficient balance before provider contact.
- [ ] 5.5 Implement exactly-once settlement from normalized usage, including release of unused reservation and explicit settlement-review state for uncovered overage.
- [ ] 5.6 Implement policy-driven release/refund behavior for pre-call cancellation, provider rejection, provider-billed failure, timeout, business parsing failure, and success.
- [ ] 5.7 Link every new AI point ledger entry to execution version, domain resource, attempt/call when applicable, policy version, and idempotency key.
- [ ] 5.8 Implement account-versus-ledger reconciliation and operator-visible mismatch reporting without rewriting historical entries.

## 6. Shared Task APIs and Frontend Foundation

- [ ] 6.1 Add controller contract tests for task creation response, detail, cancel, retry, regeneration, authorization, and tenant/project isolation.
- [ ] 6.2 Add shared execution detail, cancel, retry, and regeneration APIs with canonical progress, result, error, usage-cost, and settlement fields.
- [ ] 6.3 Add compatibility/versioning behavior for existing synchronous AI endpoints and document the eventual breaking cutover.
- [ ] 6.4 Regenerate `frontend/src/services/ant-design-pro/` through the OpenAPI command after controller contracts stabilize.
- [ ] 6.5 Add a shared frontend task polling model/service with terminal-state handling, retry/cancel actions, and stale-page recovery.
- [ ] 6.6 Add reusable task progress and failure presentation integrated with domain pages without exposing provider-specific statuses.

## 7. AI Image Migration

- [ ] 7.1 Add regression tests reproducing duplicate image execution, lost post-commit dispatch, canceled-before-call settlement, and wrong latest-log association.
- [ ] 7.2 Create an image execution handler that loads the existing image task, calls `AiInvocationService`, persists results, and links the returned call-log ID directly.
- [ ] 7.3 Replace image `@Async` correctness dependency with durable execution dispatch and atomic claim while retaining optional immediate wake-up.
- [ ] 7.4 Migrate image point deduction to reservation/settlement using image count and configured policy, including regeneration as a new execution version.
- [ ] 7.5 Update image frontend creation, progress, cancel, retry, and result refresh flows to use the shared task contract.

## 8. Script Analysis and Review Migration

- [ ] 8.1 Add contract tests proving script generate, rewrite, extraction, prompt generation, analysis stages, episode-summary fan-out, and review return durable tasks and preserve domain results.
- [ ] 8.2 Add execution-linked domain operation records or resumable redacted inputs for script operations that currently call AI before creating a task/result record.
- [ ] 8.3 Implement script generation, rewrite, extraction, and prompt handlers and convert their synchronous controllers to task-oriented compatibility responses.
- [ ] 8.4 Migrate script-analysis stages and episode-summary attempts to shared execution/attempt identities while preserving stage progress and successful sibling stages.
- [ ] 8.5 Migrate review execution and retry to shared claim, attempt, usage, cost, and settlement behavior while preserving issue-round results.
- [ ] 8.6 Update script and review frontend pages to follow shared execution status and load existing domain workspaces/results after success.

## 9. Video and Remaining Workflow Migration

- [ ] 9.1 Add regression tests for duplicate video submission, provider-native polling retries, timeout, cancellation, result download failure, and settlement correlation.
- [ ] 9.2 Move AI video submission/query HTTP and provider status normalization into provider adapters returning accepted/completed invocation outcomes.
- [ ] 9.3 Replace AI video manual call-log writes and domain-specific query attempts with shared invocation, attempt, usage, cost, and settlement records.
- [ ] 9.4 Migrate video decomposition phases to the shared execution header while retaining its episode/analysis/draft domain tables and existing result contracts.
- [ ] 9.5 Inventory and migrate provider-contacting shot voice, compose, and remaining AI workflows to registered handlers or explicitly classify non-AI local work.
- [ ] 9.6 Update video and production frontend pages to use shared task progress and canonical retry/cancel behavior.

## 10. Configuration, Operations, and Final Verification

- [ ] 10.1 Add platform APIs and permission tests for publishing model price versions, point policy versions, and viewing usage/cost/settlement details without exposing secrets.
- [ ] 10.2 Enforce model capability records in routing and replace status-only provider testing with real credential, endpoint, and model connectivity validation.
- [ ] 10.3 Add scene-specific prompt/response summary redaction and tests for API keys, credentials, and sensitive content.
- [ ] 10.4 Add operational queries and UI views for expired claims, retry exhaustion, unpriced/incomplete usage, settlement review, provider failure rate, cost, points, and reconciliation.
- [ ] 10.5 Stop creating legacy models during read paths and document the staged retirement of `ai_service_config`, old gateways, direct provider HTTP, and compatibility endpoints.
- [ ] 10.6 Run targeted backend tests after each migrated workflow, then the complete backend test suite.
- [ ] 10.7 Run frontend unit tests, type checking, Biome lint, Ant Design lint, and production build after task-contract migration.
- [ ] 10.8 Verify database migrations and rollback behavior on a production-like snapshot, including legacy point and call-log history.
- [ ] 10.9 Update AI invocation, execution, pricing, cost, point settlement, retry, cancellation, and operator runbook documentation.
