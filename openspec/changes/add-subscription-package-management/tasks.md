## 1. Domain Schema and Permissions

- [x] 1.1 Add migrations for package, price version, entitlement, order, payment, subscription, grant, and audit tables.
- [x] 1.2 Add unique keys for merchant order number, subscription period grant, and ledger idempotency references.
- [x] 1.3 Add platform operation permissions and team `BILLING:MANAGE` permission with tenant-scoped checks.
- [x] 1.4 Add entities, mappers, enums, request objects, and response objects for the commercial domain.

## 2. Package Catalog and Entitlements

- [x] 2.1 Implement package draft, publish, unpublish, and version history services.
- [x] 2.2 Validate package period, price, effective interval, and immutable referenced versions.
- [x] 2.3 Implement fixed entitlement templates for one-time points, periodic points, and global discount.
- [x] 2.4 Reject unsupported runtime entitlements such as free generations, concurrency, and member limits.
- [x] 2.5 Add platform APIs and frontend management screens for package and entitlement versions.

## 3. Orders and WeChat Native Payment

- [x] 3.1 Implement team-admin order creation with package and price snapshots.
- [x] 3.2 Integrate WeChat Native order creation and QR-code payment parameters using configuration-backed credentials.
- [x] 3.3 Implement callback signature verification and merchant-order/amount validation.
- [x] 3.4 Implement the order state machine, 30-minute unpaid closure, active order query, and proactive payment lookup.
- [x] 3.5 Make callbacks, lookup, closure, and operator reconciliation idempotent.
- [x] 3.6 Persist payment notifications, exceptions, and reconciliation evidence; do not implement refunds.

## 4. Point Grants and Subscription Lifecycle

- [x] 4.1 Implement payment-success entitlement orchestration with `ENTITLEMENT_PENDING` retry state.
- [x] 4.2 Grant one-time package points through the existing team account and append-only ledger with order idempotency keys.
- [x] 4.3 Implement first activation, same-package renewal tail extension, and other-package queued activation.
- [x] 4.4 Implement subscription period calculation anchored to activation day and unique `subscriptionId + periodNo` grants.
- [x] 4.5 Implement periodic grant scheduler, retry, failure alerting, and permanent point validity.
- [x] 4.6 Add team APIs and UI for current subscription, queued orders, grant history, and point balance links.

## 5. AI Discount Integration

- [x] 5.1 Implement an entitlement resolver returning the team's active global discount at execution creation time.
- [x] 5.2 Extend execution billing snapshots with subscription/version, pre-discount points, discount rate, and final points.
- [x] 5.3 Apply the discount after model point-price calculation and round to 8 decimal places with half-up rounding.
- [x] 5.4 Ensure retries reuse the frozen commercial snapshot and new regenerations resolve current active entitlements.
- [x] 5.5 Add tests for active, expired, queued, duplicate, and no-subscription discount scenarios.

## 6. Verification and Operations

- [x] 6.1 Add backend tests for package lifecycle, permissions, order state transitions, callback duplication, and amount mismatch.
- [x] 6.2 Add integration tests covering payment success through point grant, subscription activation, periodic grant, and ledger reconciliation.
- [x] 6.3 Add frontend tests for package display, permission gating, QR payment polling, order status, and subscription history.
- [x] 6.4 Add operational runbook entries for WeChat configuration, callback failures, pending grants, queued subscriptions, and reconciliation.
- [x] 6.5 Run backend and frontend tests, type checks, Biome/Ant Design lint, production build, and authorized/non-authorized smoke tests.
