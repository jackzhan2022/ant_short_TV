## Context

The current schema has a tenant balance snapshot in `team_point_account`, a legacy one-step transaction table in `team_point_transaction`, and newer AI reservation and AI-only ledger tables. `TeamPointService.consumeForAi()` still performs an immediate deduction, while migrated AI handlers reserve and settle points around a durable execution. The product is not launched, so development data does not need a compatibility-preserving backfill.

The implementation must use the existing Spring Boot, MySQL, MyBatis-Plus, and `JdbcTemplate` stack. It must support concurrent requests, retries, provider failures, cancellation, incremental reservation, administrative grants, and tenant-scoped transaction queries without introducing a broker or a second accounting service.

## Goals / Non-Goals

**Goals:**

- Make one point ledger the append-only source of historical point movements.
- Keep `team_point_account` as a query-optimized snapshot whose values are changed only with a ledger append.
- Keep an AI reservation as a lifecycle document, linked to one execution version and its ledger entries.
- Make grants, adjustments, AI reservation, settlement, release, and refund idempotent and concurrency-safe.
- Remove all production AI calls to `consumeForAi()` and expose one transaction-history contract.
- Provide migration rehearsal, architecture, concurrency, and reconciliation tests.

**Non-Goals:**

- Do not redesign AI execution dispatch, provider routing, usage-cost calculation, subscriptions, invoices, payments, or currency accounting.
- Do not rewrite historical development transactions; the destructive migration may start the unified ledger at the cutover balance.
- Do not remove the account snapshot or the AI reservation table; they have distinct responsibilities.

## Decisions

### 1. Use a generalized append-only ledger

Create `point_ledger` with a stable entry type (`GRANT`, `ADJUST`, `RESERVE`, `INCREMENTAL_RESERVE`, `SETTLE`, `RELEASE`, `REFUND`), signed or semantically typed amount, tenant/user/business correlation, optional reservation/execution/attempt/call-log/policy references, idempotency key, and available/reserved balances after the entry. A unique key on the tenant and idempotency key makes retries return the existing result.

`team_point_transaction` and `ai_point_ledger` are removed after code is migrated. A compatibility read endpoint may retain its response shape, but it reads `point_ledger` through the new mapper and does not write the old table.

### 2. Keep account mutation and ledger append in one transaction

All commands enter `PointAccountingService`. The service locks or conditionally updates the tenant account, validates sufficient available or reserved balance, updates cumulative totals and `version`, appends exactly one ledger entry, and commits as one database transaction. No controller, AI handler, or admin service may update `team_point_account` directly.

For a reservation, available balance decreases and reserved balance increases. Settlement removes the reservation amount from reserved balance, increments consumed by actual charge, and returns unused points to available balance. Release returns the unused reservation. Refund increases available balance and increments refunded totals without rewriting the original entry.

### 3. Preserve reservation as a lifecycle document

`ai_point_reservation` remains one row per execution version. It stores policy version, authorized usage, reserved/settled/released/refunded amounts, status, and idempotency data. Its status transitions are validated; the ledger is the immutable evidence of each transition. Reservation creation happens in the same transaction as AI execution creation before provider contact.

### 4. Separate administrative and AI commands behind one boundary

Administrative grant and adjustment endpoints call the same accounting service with `GRANT` or `ADJUST`. AI execution services call reservation, settle, release, or refund commands. The service rejects an AI command without an execution or reservation correlation and rejects duplicate idempotency keys with conflicting payloads.

### 5. Remove compatibility deduction rather than silently redirecting it

Delete `consumeForAi()` and its production call sites. Parameterless synchronous AI compatibility methods must either be removed or fail with an explicit migration error until the caller creates an execution and reservation. This prevents a hidden second path from surviving behind a deprecated method.

### 6. Reconcile from the ledger without rebuilding history at runtime

Expose tenant and platform reconciliation queries comparing the account snapshot to the latest ledger balances and reservation totals. Mismatches are reported for operator action; normal requests do not rewrite ledger history or silently repair balances.

## Risks / Trade-offs

- [A partial migration could leave old callers] → Add an architecture test forbidding `consumeForAi`, old table writes, and direct account updates in production code before deleting the old tables.
- [Concurrent reservations could overspend] → Use conditional balance updates or row locking, a version increment, and concurrency tests with insufficient-balance assertions.
- [A retry could append duplicate money movements] → Require tenant-scoped idempotency keys and test identical and conflicting retries.
- [Dropping legacy tables loses development history] → This is intentional before launch; run a migration rehearsal and document that no production backfill is supported.
- [Existing frontend transaction fields differ] → Preserve a response adapter temporarily while changing the backend source to `point_ledger`, then regenerate clients.

## Migration Plan

1. Add failing schema, service, architecture, and controller tests for unified ledger behavior.
2. Add the cutover migration and new ledger entity/mapper; initialize accounts from their current snapshot and do not copy legacy development rows.
3. Implement `PointAccountingService` and route admin grant/adjustment plus AI reserve/settle/release/refund through it.
4. Migrate transaction queries and platform operations to the unified ledger; remove old AI direct-deduction call sites and compatibility methods.
5. Run targeted point, AI execution, migration, concurrency, and frontend contract tests, then run the complete required verification suite.
6. On rollback before launch, disable the new routes and restore the previous code/database snapshot. Do not attempt to merge new ledger rows back into the removed legacy tables.

## Open Questions

- Whether to keep the physical table name `ai_point_reservation` for V0 or rename it to `point_reservation`; the first implementation can keep the existing name while changing ownership semantics.
- Whether the transaction-history API should expose signed amounts or separate `availableDelta` and `reservedDelta`; the design should choose one canonical response before frontend regeneration.
