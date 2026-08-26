## ADDED Requirements

### Requirement: Unified append-only ledger
The system MUST record every point movement for a tenant in one append-only `point_ledger`, including grants, adjustments, AI reservations, incremental reservations, settlements, releases, and refunds. Ledger entries MUST NOT be updated or deleted after insertion.

#### Scenario: Administrative grant is recorded
- **WHEN** an authorized operator grants points to a tenant
- **THEN** the system appends one `GRANT` ledger entry with the tenant, operator, amount, reason, idempotency key, and resulting available balance

#### Scenario: AI settlement is recorded
- **WHEN** an AI reservation is settled or released
- **THEN** the system appends the corresponding ledger entry linked to execution version, reservation, attempt or call log when available, and policy version

### Requirement: Snapshot and ledger are atomically consistent
The system MUST update `team_point_account` and append its corresponding ledger entry in the same database transaction. A committed account mutation MUST have a committed ledger entry, and a failed mutation MUST change neither.

#### Scenario: Database failure rolls back a grant
- **WHEN** the ledger insert fails after the account update is attempted
- **THEN** the account update is rolled back and no partial grant is visible

#### Scenario: Reconciliation detects divergence
- **WHEN** the account snapshot differs from the latest ledger balance or reservation totals
- **THEN** reconciliation reports a mismatch with tenant and balance details without rewriting ledger history

### Requirement: AI reservation precedes provider contact
Every provider-backed production AI execution MUST create an execution-linked `ai_point_reservation` and reserve the authorized point amount before contacting a provider. A failed reservation MUST prevent provider contact.

#### Scenario: Insufficient balance blocks AI contact
- **WHEN** an AI execution has insufficient available points
- **THEN** reservation fails, the execution remains uncontacted, and no provider call log or point settlement is created

#### Scenario: Pre-call cancellation releases reservation
- **WHEN** an execution is canceled before provider contact
- **THEN** the reservation transitions to released, the reserved amount returns to available balance, and one `RELEASE` ledger entry is appended

### Requirement: Reservation settlement is lifecycle-safe
The system MUST support `RESERVED`, `SETTLED`, `RELEASED`, `REFUNDED`, and `SETTLEMENT_REVIEW_REQUIRED` outcomes with valid transitions. Settlement MUST charge actual policy usage and release unused reservation; unexpected overage MUST use incremental reservation or enter review rather than creating a negative balance.

#### Scenario: Successful execution settles actual usage
- **WHEN** an execution succeeds with actual usage lower than its reservation
- **THEN** actual points are consumed, unused points are released, the reservation is settled, and both ledger entries are correlated to the execution

#### Scenario: Overage without balance enters review
- **WHEN** actual usage exceeds the reservation and the tenant cannot fund the overage
- **THEN** the reservation enters `SETTLEMENT_REVIEW_REQUIRED` and the system does not make the account balance negative

### Requirement: Idempotent point commands
Every grant, adjustment, reservation, settlement, release, and refund command MUST require an idempotency key. Repeating an identical command MUST return the original result without a second balance mutation; reusing a key with different material fields MUST fail.

#### Scenario: Duplicate reservation request
- **WHEN** the same execution version submits the same reservation command twice
- **THEN** the system returns the existing reservation and leaves account and ledger totals unchanged after the first commit

#### Scenario: Conflicting retry is rejected
- **WHEN** an idempotency key is reused with a different tenant, amount, or execution
- **THEN** the system rejects the request with a validation or conflict error and appends no ledger entry

### Requirement: Single production mutation boundary
Production code MUST NOT call `TeamPointService.consumeForAi()`, write `team_point_transaction` or `ai_point_ledger`, or update `team_point_account` outside the unified accounting service. Administrative and AI callers MUST use the accounting service commands.

#### Scenario: Legacy direct deduction is unavailable
- **WHEN** a production AI workflow attempts to use the legacy direct-deduction method
- **THEN** the codebase fails architecture or compilation checks and the workflow cannot bypass reservation and settlement

#### Scenario: Transaction history reads unified ledger
- **WHEN** a tenant requests point history
- **THEN** the API reads `point_ledger` and returns entries scoped to that tenant without querying either legacy transaction table
