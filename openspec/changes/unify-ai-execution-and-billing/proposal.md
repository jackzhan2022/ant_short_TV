## Why

AI workflows currently mix synchronous request-thread calls, in-process asynchronous execution, scheduler-driven tasks, provider-native asynchronous jobs, legacy gateway paths, and manually written call logs. Point consumption is a separate fixed one-point operation, while provider cost is always recorded as zero, so the platform cannot reliably prevent duplicate calls, reconcile a business task with its provider cost and point transaction, or support per-call, per-image, per-token, and per-second billing.

## What Changes

- **BREAKING** Make every user-facing AI operation create or reuse a durable execution task and return task state instead of waiting for the provider result in the request thread; existing synchronous response contracts require a compatibility migration.
- Add a shared AI execution lifecycle and attempt model that links domain tasks, provider calls, call logs, usage records, and point settlement.
- Require atomic task claiming, stable execution idempotency, timeout recovery, retry eligibility, and durable post-commit dispatch for all AI workflows, including image, script, review, video, and future audio workflows.
- Route provider-facing work through the unified AI invocation boundary; adapters may execute synchronously or manage a provider-native asynchronous job without changing the business task contract.
- Record structured usage line items for calls, input/output tokens, images, video seconds, audio seconds, characters, and other supported meters.
- Add effective-dated, versioned model pricing and immutable price snapshots so each usage line can produce an auditable provider cost.
- Separate provider cost accounting from customer point pricing.
- Replace direct fixed point deduction with idempotent reserve, settle, release, and refund transactions linked to the execution task and invocation attempts.
- Standardize task, attempt, invocation, usage, cost, and settlement observability, including trace IDs, canonical statuses, normalized errors, and sensitive-summary redaction.
- Migrate existing workflows incrementally while preserving existing domain result records and read APIs during the transition.

## Capabilities

### New Capabilities

- `ai-execution-lifecycle`: Durable asynchronous business-task creation, dispatch, state transitions, provider execution modes, cancellation, retry, result linking, and client-visible progress for all AI operations.
- `ai-usage-cost-accounting`: Structured multi-meter usage capture, effective-dated model pricing, immutable price snapshots, cost calculation, currency handling, and auditable aggregation.
- `ai-point-settlement`: Idempotent point reservation, final settlement, release, refund, pricing policies, and reconciliation with execution tasks, attempts, call logs, and provider cost.

### Modified Capabilities

- `ai-task-execution-reliability`: Extend atomic claiming, execution idempotency, attempt history, timeout recovery, and observable outcomes to every durable AI execution, and require recovery when post-commit dispatch or a worker process is interrupted.

## Impact

- Backend AI modules under `com.antshorttv.ai`, domain task services for script, review, image, video, and shot production, schedulers, provider adapters, point services, and call-log queries.
- Database migrations for shared execution tasks and attempts, usage and cost lines, price versions, point reservations and settlements, correlation identifiers, and uniqueness constraints.
- Existing synchronous AI endpoints will transition to task-oriented responses; compatibility handling may be required for generated frontend clients and current pages.
- Frontend AI workflows will consume a shared task status/progress contract and refresh or subscribe until terminal completion.
- Operations will gain provider-cost, point-settlement, failure, retry, and reconciliation views; API keys and prompt/response summaries remain subject to current security boundaries and additional redaction.
