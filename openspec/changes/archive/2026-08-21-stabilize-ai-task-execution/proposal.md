## Why

Current AI task workflows can reach real providers from several schedulers and services, but task claiming, retry rules, idempotency, and observability are implemented per feature. This makes duplicate provider calls, unsafe retries, and hard-to-debug "transport success but business failure" states more likely as video decomposition and other AI workflows expand.

## What Changes

- Introduce a shared reliability contract for asynchronous AI task execution.
- Add atomic task claiming so pending tasks are claimed by only one worker before any external AI call starts.
- Add idempotency keys for provider-facing execution phases so the same task phase/version cannot silently trigger duplicate calls.
- Standardize retry eligibility and attempt recording across AI task workflows.
- Standardize AI task observability fields so logs distinguish provider transport status, business parsing status, task status, and retryability.
- Apply the reliability contract first to high-cost AI workflows, especially video decomposition and AI video generation, while keeping existing public API behavior stable.

## Capabilities

### New Capabilities

- `ai-task-execution-reliability`: Defines reliable execution requirements for asynchronous AI tasks, including atomic claiming, idempotency, retry rules, attempts, and observability.

### Modified Capabilities

- None.

## Impact

- Backend AI task services and schedulers under `backend/src/main/java/com/antshorttv/video`, with later reuse by image, shot, and compose workflows.
- Flyway migrations for claim/idempotency/attempt metadata if existing tables do not already contain the required fields.
- Backend tests for duplicate scheduler execution, retry state validation, attempt history, and log semantics.
- No breaking frontend API changes are planned; UI may surface clearer task status and failure messages using existing response shapes where possible.
