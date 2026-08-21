## Context

The backend already has several asynchronous or long-running AI workflows: AI video task polling, video script decomposition, AI image generation, shot voice/compose, and episode compose. These workflows share the same operational risks: real provider calls cost money, scheduler retries can duplicate work, and a successful HTTP response can still fail business parsing.

The immediate pressure comes from video script decomposition and AI video generation. Both are under `backend/src/main/java/com/antshorttv/video`, both can call external providers, and both need strong retry and diagnostics behavior before more providers or higher-volume usage are added.

## Goals / Non-Goals

**Goals:**

- Make scheduler-driven AI calls safe under concurrent workers and repeated polling.
- Prevent duplicate provider calls for the same task phase/version unless the user explicitly regenerates.
- Enforce retry eligibility from server-side task state, not just frontend button visibility.
- Persist complete attempt and diagnostic information for troubleshooting.
- Introduce a reusable pattern that can later be applied to image, shot, and compose workflows.

**Non-Goals:**

- Replace the existing AI Gateway or provider adapter architecture.
- Redesign frontend task pages or introduce breaking API response changes.
- Implement a distributed queue system such as Redis, RabbitMQ, or Kafka in this change.
- Refactor every AI workflow at once.

## Decisions

### Decision 1: Use database-backed atomic claiming first

The implementation will use conditional SQL updates such as `update ... where id = ? and status in (...) and execution_token is null` to claim work before provider calls. The update count determines whether the worker owns the task.

This fits the current MySQL/Flyway/MyBatis Plus stack and avoids introducing a new queue dependency. A queue can be considered later if throughput or delayed scheduling requirements grow beyond what polling can handle.

### Decision 2: Introduce a small shared execution component

Add a backend component for AI task execution reliability, responsible for common operations:

- claim a task phase
- allocate an attempt number
- create and update attempt records
- build stable idempotency keys
- detect stale running executions
- expose retryability decisions

Workflow services still own domain-specific behavior: video decomposition keeps its analysis/draft phases, AI video keeps submit/query semantics, and later image or shot workflows can opt in without inheriting unrelated state names.

### Decision 3: Use stable idempotency keys per task phase/version

Each provider-facing execution receives an idempotency key derived from tenant, workflow type, task id, phase, and execution version. Regeneration increments the execution version, which creates a new key and preserves prior successful results.

The idempotency key should be stored with the attempt or task execution metadata before provider calls begin. If the same key is already running or succeeded, duplicate execution is rejected or treated as already accepted instead of calling the provider again.

### Decision 4: Centralize retry rules per workflow

Retry rules will be explicit and testable. For example, failed video analysis may retry analysis, failed draft generation may retry draft generation, but confirmed, successful, running, or deleted tasks must reject retry requests.

The shared component can provide reusable status predicates, but each workflow defines which states and phases are legal. This keeps business meaning close to the workflow while removing ad hoc retry behavior.

### Decision 5: Keep observability persistent and linked

Attempts must link to AI call logs when available. AI call logs should differentiate transport success, provider errors, timeout/rate-limit errors, and business parsing failures. Task detail APIs should expose the latest attempt, linked log id, error code/message, current phase, and retryability where authorization allows.

## Risks / Trade-offs

- Existing tables differ by workflow -> Start with video decomposition and AI video task tables, then extract the common pattern only where duplication is real.
- Conditional SQL can become database-specific -> Keep SQL small and isolated in mapper/helper methods; document MySQL assumptions in migrations/tests.
- More execution metadata increases schema complexity -> Add only fields required for claim ownership, idempotency, timeout recovery, and diagnostics.
- Long-running provider calls inside transactions can hold locks -> Claim and persist attempt metadata in a short transaction, then call the provider outside long database locks, then update final status.
- Existing frontend may not understand new retryability details -> Preserve existing response fields and add optional fields only where needed.

## Migration Plan

1. Add missing execution metadata through Flyway migrations for the first targeted workflows.
2. Implement the shared claiming/idempotency helper and tests with database-backed race simulations.
3. Apply the helper to video decomposition scheduler/execution first.
4. Apply the same pattern to AI video task polling/submission where the current schema allows.
5. Add timeout recovery and retry eligibility tests.
6. Keep rollback simple by preserving old statuses and adding nullable metadata columns; if needed, disable the scheduler through configuration while investigating.

## Open Questions

- Should idempotency keys also be sent to providers that support native idempotency headers, or only used internally at first?
- Should retryability be exposed as a field in all task detail responses now, or introduced only for video decomposition first?
- Should stale running timeout recovery be scheduler-driven or handled by a separate maintenance job?
