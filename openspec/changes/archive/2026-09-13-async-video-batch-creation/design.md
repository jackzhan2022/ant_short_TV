## Context

`VideoDecompositionService.create` currently wraps batch creation in one transaction and loops over every submitted video. For each episode it inserts the episode, synchronously calls `AiExecutionService.createWithReservation`, updates the episode with the execution ID, and inserts the initial attempt. The synchronous execution call resolves billing versions and subscription discounts, updates the tenant point account, and inserts reservation and ledger records. A 12-episode request therefore performs the complete accounting initialization path 12 times before it can return.

The background path already supports lazy execution initialization. `VideoDecompositionExecutionService.executeEpisode` calls `ensureExecutionHeader`, which creates an execution with the stable episode-based idempotency key when `execution_id` is null, writes the ID back to the episode, and only then starts the provider-facing attempt. The scheduler atomically claims an episode before calling this service.

The change must retain the current HTTP contract, direct video-to-screenplay behavior, immutable result storage, accounting guarantees, and compatibility with existing and historical episodes.

## Goals / Non-Goals

**Goals:**

- Bound batch creation work to durable queue-record persistence rather than per-episode accounting initialization.
- Return a successful batch response after the batch, ordered episodes, and initial attempts commit.
- Create each episode's execution, billing snapshot, and point reservation idempotently after background claim and before provider contact.
- Keep execution-initialization failures observable on the affected episode without deleting the batch.
- Preserve existing retry, progress, settlement, and immutable screenplay-result behavior.

**Non-Goals:**

- Increasing the scheduler batch size or running episode provider calls concurrently.
- Introducing a message broker, outbox, new table, or new public API.
- Changing upload behavior, screenplay format, provider selection, pricing rules, or frontend screens.
- Backfilling execution IDs for pending historical episodes.

## Decisions

### Keep the batch creation transaction, but remove execution initialization from it

The creation transaction will continue to insert the batch, all ordered episode rows, and one initial `VIDEO_ANALYSIS/PENDING` attempt per episode. It will stop invoking the private eager `createExecutionHeader` path. This preserves all-or-nothing queue persistence while removing billing queries, point-account writes, reservation writes, and execution-task updates from the request latency.

Alternative: bulk the current synchronous writes. Rejected because billing resolution and the shared point-account update still occur per episode and can still block or fail the whole transaction.

### Reuse `ensureExecutionHeader` as the single lazy initialization boundary

After the scheduler atomically claims a pending episode, `executeEpisode` will continue to call `ensureExecutionHeader`. When `execution_id` is null it creates the billable execution with `video-decomposition:{episodeId}`, reserves points, and writes the execution ID to the episode before any provider call. Existing idempotency constraints prevent duplicate execution/reservation creation after worker retries.

Alternative: add a separate initialization scheduler or status. Rejected because the current execution method already owns this boundary and a new intermediate state would add failure transitions without improving durability.

### Keep the initial attempt in the creation transaction

The existing provider execution path expects the current `VIDEO_ANALYSIS` attempt to exist. Creating this lightweight pending row with the episode avoids a new conditional attempt-creation path and keeps attempt history visible immediately. Its nullable `execution_id` is populated or correlated by the existing execution flow.

Alternative: create the attempt only after claim. Rejected for this change because it expands scheduler responsibilities and changes observable attempt history without contributing materially to request latency.

### Treat creation success as durable enqueue, not billing authorization

Insufficient points and incomplete billing configuration will be detected when a worker initializes a claimed episode. Claim recovery will persist the domain episode and attempt failure even when initialization fails before an execution ID exists; shared execution and settlement records are updated only when they were created. The batch remains queryable and sibling episodes retain their own states.

Alternative: preflight billing once during creation. Rejected because a preflight would not create a frozen per-execution billing snapshot, could race with configuration changes, and would reintroduce a synchronous billing dependency.

## Risks / Trade-offs

- [Risk] Users learn about insufficient points or missing pricing after batch creation rather than immediately. → Mitigation: persist the initialization error on the episode and expose it through existing batch/episode progress responses.
- [Risk] A worker crash between execution creation and episode ID update could retry initialization. → Mitigation: use the existing stable client idempotency key and unique execution constraints so retry reuses the same execution and reservation.
- [Risk] The current single-episode scheduler still makes total generation time long for large batches. → Mitigation: keep this change focused on request latency and address concurrency in a separate measured change.
- [Risk] Removing eager initialization could expose assumptions that every pending episode has an execution ID. → Mitigation: add service and scheduler/execution regression tests for pending episodes with null `execution_id`.

## Migration Plan

1. Deploy the backend change with no schema or frontend migration.
2. Verify a multi-episode create request returns after queue persistence and creates no execution/reservation rows synchronously.
3. Verify the scheduler claims a new episode, creates exactly one execution/reservation, and proceeds to provider invocation.
4. Monitor episode initialization failures and batch creation latency after deployment.

Rollback is code-only. The previous execution service already supports null `execution_id` through `ensureExecutionHeader`, so batches created by the new version remain processable after rollback. No data deletion or transformation is required.

## Open Questions

None.
