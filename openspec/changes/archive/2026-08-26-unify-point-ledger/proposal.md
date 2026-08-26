## Why

The current point system has two authoritative mutation paths: legacy grants, adjustments, and direct AI deductions update `team_point_account` and write `team_point_transaction`, while migrated AI workflows update the same account through `ai_point_reservation` and `ai_point_ledger`. Because the product has not launched and legacy development data may be discarded, consolidating now prevents divergent balances, incomplete audit history, and future settlement ambiguity.

## What Changes

- Introduce one append-only `point_ledger` as the sole history for every point movement, including grant, adjustment, reserve, incremental reserve, settle, release, and refund.
- Retain `team_point_account` as the tenant balance snapshot with available and reserved balances, cumulative totals, and an optimistic version; all mutations must update the snapshot and append the corresponding ledger entry in one transaction.
- Retain the AI reservation lifecycle as `ai_point_reservation`, linked to the unified ledger, execution version, business resource, attempt, call log, and point-policy version.
- **BREAKING** Remove direct AI deduction through `TeamPointService.consumeForAi()` and require every provider-backed production AI workflow to create an execution-linked reservation before provider contact.
- **BREAKING** Replace `team_point_transaction` and the AI-only `ai_point_ledger` with the unified ledger, migrate no legacy development history, and update transaction query APIs to read the new ledger contract.
- Route administrative grants and adjustments through the same point accounting service and idempotent ledger mutation boundary used by AI settlement.
- Add reconciliation and architecture checks that detect snapshot/ledger divergence and prohibit production code from bypassing the unified mutation service.

## Capabilities

### New Capabilities

- `point-accounting`: Defines the unified append-only point ledger, account snapshot invariants, administrative grant/adjustment behavior, AI reservation and settlement lifecycle, idempotency, correlation, and reconciliation requirements.

### Modified Capabilities

None.

## Impact

- Backend point schema and Flyway migrations for `team_point_account`, `team_point_transaction`, `ai_point_reservation`, and `ai_point_ledger`.
- Point services, controllers, response contracts, AI execution creation/settlement handlers, script compatibility paths, and platform accounting/operations queries.
- Backend architecture, migration, concurrency, idempotency, controller, AI workflow, and reconciliation tests.
- Generated frontend point transaction contracts and any pages that display point history; generated clients must be regenerated through the existing OpenAPI workflow rather than edited manually.
- The point-settlement portion of `unify-ai-execution-and-billing` is superseded by this focused change; execution, usage, and provider-cost behavior remain outside this change.
