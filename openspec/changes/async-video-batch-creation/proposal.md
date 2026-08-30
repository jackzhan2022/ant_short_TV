## Why

Creating a video decomposition batch currently performs per-episode AI execution setup, billing resolution, point reservation, and ledger writes inside the request transaction. Multi-episode batches therefore keep the browser waiting, amplify database and account-lock latency, and can roll back the whole batch when one episode's execution initialization fails.

## What Changes

- Make batch creation persist only the batch, ordered episode records, and initial pending attempt records before returning.
- Defer AI execution creation, billing snapshot resolution, and point reservation until a background worker claims each episode.
- Preserve atomic creation of the batch and all ordered episodes so a successful response always represents a durable queued batch.
- Preserve existing execution idempotency, retry behavior, progress responses, direct screenplay output, and frontend API contracts.
- Surface background execution-initialization failures on the affected episode instead of rolling back an already-created batch.
- Keep the current video decomposition worker concurrency unchanged in this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `video-script-decomposition`: Batch creation becomes a fast durable enqueue operation whose success does not require synchronous AI execution or point-reservation initialization.
- `ai-task-execution-reliability`: A video decomposition execution and its frozen billing reservation are created idempotently after the episode is claimed, before provider contact, rather than inside the batch creation request.

## Impact

- Backend video decomposition batch creation service and its service/controller tests.
- Background video decomposition execution initialization and scheduler regression tests.
- Existing database schema, HTTP request/response shapes, frontend behavior, object storage, and provider integrations remain compatible.
- Accounting timing changes: insufficient points or incomplete billing configuration becomes an episode-level background failure instead of a synchronous whole-batch creation failure.
