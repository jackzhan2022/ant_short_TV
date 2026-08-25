## ADDED Requirements

### Requirement: Points are reserved before billable execution
The system SHALL atomically reserve the maximum authorized point amount before the first billable provider call and SHALL reject execution without contacting the provider when available balance is insufficient.

#### Scenario: Balance is sufficient
- **WHEN** a pending execution requires a point reservation and the tenant has sufficient available balance
- **THEN** the system creates one idempotent reservation linked to the execution version before the provider call

#### Scenario: Balance is insufficient
- **WHEN** the required reservation exceeds the tenant's available balance
- **THEN** the task fails or remains blocked with `TEAM_POINTS_INSUFFICIENT` and no provider call is made

### Requirement: Point pricing is separate from provider cost
The system SHALL calculate customer points from a versioned point-pricing policy that may use per-call, per-image, per-token, per-second, fixed scene, or composite rules independently of provider currency cost.

#### Scenario: Fixed scene pricing differs from provider cost
- **WHEN** a business scene is configured to charge five points per successful generation
- **THEN** the settlement charges five points even though the provider cost is recorded separately in currency

#### Scenario: Usage-based point pricing is configured
- **WHEN** a video scene is priced by generated second
- **THEN** final point settlement uses the measured video duration and the policy version captured for the execution

### Requirement: Reservations are settled exactly once
The system SHALL settle a reservation idempotently when final billable usage is known, converting the chargeable amount into consumed points and releasing any unused reserved amount.

#### Scenario: Actual charge is less than reservation
- **WHEN** final point pricing produces a charge below the reserved amount
- **THEN** the final charge is consumed and the unused remainder becomes available in the same settlement transaction

#### Scenario: Settlement is retried
- **WHEN** the same settlement command is processed more than once
- **THEN** the point account and ledger reflect exactly one settlement

### Requirement: Cancellation and failure follow explicit settlement policy
The system SHALL apply a versioned settlement policy that distinguishes no-call cancellation, provider rejection, provider-billed failure, timeout, business parsing failure, and successful completion.

#### Scenario: Task is canceled before provider call
- **WHEN** a reserved task is canceled before any billable attempt starts
- **THEN** the full reservation is released and no consumption transaction is created

#### Scenario: Provider call is billable but business parsing fails
- **WHEN** the provider completed a billable call but the business result is unusable
- **THEN** settlement applies the configured failure policy while preserving provider usage and cost records

### Requirement: Point ledger entries are immutable and correlated
The system SHALL record append-only point reservation, settlement, release, refund, grant, and adjustment entries linked to tenant, user, execution task, execution version, business resource, attempt when applicable, call log when applicable, pricing policy version, and idempotency key.

#### Scenario: Operator traces a point charge
- **WHEN** an operator inspects an AI point transaction
- **THEN** the system can resolve the originating execution, business result, invocation attempts, provider usage, cost, and applied point policy

### Requirement: Retry and regeneration do not double charge silently
The system SHALL distinguish infrastructure retries within an execution version from intentional regeneration and SHALL apply point policy idempotently at the configured billable boundary.

#### Scenario: Worker retries after a transient pre-call failure
- **WHEN** an attempt fails before contacting the provider and the worker retries the same execution version
- **THEN** no additional reservation or consumption is created

#### Scenario: User intentionally regenerates
- **WHEN** a user creates a new execution version to regenerate output
- **THEN** the new version receives its own reservation and settlement while prior charges remain auditable

### Requirement: Account balances reconcile with ledger entries
The system SHALL provide a reconciliation check proving that account totals and available or reserved balances equal the net immutable ledger movements.

#### Scenario: Reconciliation detects a mismatch
- **WHEN** stored account totals differ from ledger-derived totals
- **THEN** the system reports the affected tenant and execution correlations without silently rewriting historical entries

