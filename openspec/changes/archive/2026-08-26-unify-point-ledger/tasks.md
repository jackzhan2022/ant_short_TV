## 1. Lock the accounting contract with tests

- [x] 1.1 Add architecture tests forbidding `TeamPointService.consumeForAi()`, writes to `team_point_transaction`/`ai_point_ledger`, and direct production updates to `team_point_account` outside the accounting service.
- [x] 1.2 Add service tests for grant, adjustment, reserve, incremental reserve, settle, release, refund, insufficient balance, and valid/invalid reservation transitions.
- [x] 1.3 Add concurrency and idempotency tests proving duplicate commands do not mutate balances twice and concurrent reservations cannot overspend.
- [x] 1.4 Add controller and tenant-isolation tests for unified point history and authorized administrative grant/adjustment operations.

## 2. Replace the split schema with a unified ledger

- [x] 2.1 Add a destructive Flyway migration that creates `point_ledger` with entry type, amount, balance snapshots, tenant/business correlations, reservation/execution/attempt/call-log/policy references, and a tenant-scoped idempotency uniqueness key.
- [x] 2.2 Extend or validate `team_point_account` snapshot columns and indexes for available/reserved balances, cumulative totals, and optimistic version updates.
- [x] 2.3 Preserve `ai_point_reservation` and enforce execution-version, lifecycle-status, and idempotency constraints needed to link reservations to the unified ledger.
- [x] 2.4 Drop `team_point_transaction` and `ai_point_ledger` after all application references are migrated; document that no pre-launch legacy rows are backfilled.
- [x] 2.5 Add migration rehearsal coverage for account preservation, legacy table removal, foreign keys, indexes, and clean startup from the cutover schema.

## 3. Implement the single accounting mutation boundary

- [x] 3.1 Add unified ledger entity, mapper, entry types, command/result records, and tenant-scoped query models.
- [x] 3.2 Implement `PointAccountingService` so every mutation updates the account snapshot and appends one ledger row in one transaction with idempotency checks.
- [x] 3.3 Implement atomic AI reserve, incremental reserve, settle, release, and refund behavior with policy-driven charge decisions and settlement-review handling.
- [x] 3.4 Move administrative grant and adjustment behavior from `TeamPointService` into the accounting service while preserving permission and audit metadata.
- [x] 3.5 Implement reconciliation queries comparing account snapshots, latest ledger balances, and reservation totals without rewriting ledger history.

## 4. Migrate AI workflows and remove the old path

- [x] 4.1 Update `AiExecutionService` and all migrated handlers to call the unified accounting service and link every ledger entry to execution version and reservation.
- [x] 4.2 Remove every production `consumeForAi()` call from script analysis, review, extraction, and script workflow compatibility helpers.
- [x] 4.3 Remove or fail-fast parameterless synchronous AI compatibility methods so no provider call can occur without an execution-linked reservation.
- [x] 4.4 Update AI cancellation, timeout, provider rejection, business failure, success, retry, and regeneration paths to settle, release, refund, or review exactly once.
- [x] 4.5 Add regression tests proving insufficient points prevent provider contact and duplicate callbacks do not duplicate settlement.

## 5. Migrate APIs, operations, and frontend contracts

- [x] 5.1 Update tenant point-history APIs and platform accounting/operations queries to read `point_ledger` only.
- [x] 5.2 Preserve or explicitly version response fields while replacing legacy transaction identifiers with ledger entry identifiers and entry types.
- [x] 5.3 Regenerate frontend OpenAPI clients and update point-history consumers without hand-editing generated files.
- [x] 5.4 Add operator-visible reconciliation and settlement-review reporting with tenant isolation and secret-safe payloads.

## 6. Verification and cleanup

- [x] 6.1 Run targeted point, AI execution, workflow, migration, architecture, controller, and frontend contract tests.
- [x] 6.2 Run backend complete test suite and frontend type-check, lint, antd lint, unit tests, and build.
- [x] 6.3 Run repository scans proving production code contains no `consumeForAi`, `team_point_transaction`, or `ai_point_ledger` references outside immutable historical migrations or change documentation.
- [x] 6.4 Start the application against the cutover development schema and smoke-test grant, adjustment, AI reserve/settle/release, history, and reconciliation flows.
- [x] 6.5 Mark the superseded point-settlement tasks in `unify-ai-execution-and-billing` as covered by this focused change or update that change's scope before implementation begins.
