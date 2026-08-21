# AI Task Execution Reliability

## Scope

This document describes the reliability rules for asynchronous AI tasks that can trigger real provider calls. The first supported workflows are:

- Video script decomposition episode analysis and draft generation.
- AI storyboard video task polling.

The same pattern can later be applied to AI image, shot voice, shot compose, and episode compose tasks.

## Claiming

Workers must claim a task before calling an external AI provider. A claim writes:

- `execution_token`
- `execution_phase`
- `execution_version`
- `claimed_at`
- `heartbeat_at`
- `execution_timeout_at`
- `retryable = false`

The claim is a conditional database update that only succeeds when the task is still in the expected state and has no active `execution_token`. If another worker already claimed the task, the losing worker must not call the provider.

## Idempotency

Provider-facing phases use a stable idempotency key:

```text
<workflow-type>:<task-id>:<phase>:<execution-version>
```

Regeneration or accepted retry increments the execution version and creates a new key. Duplicate execution of the same task phase/version must be skipped or rejected rather than silently issuing another provider call.

## Attempts

Every accepted execution records an attempt with:

- task id or episode id
- attempt number
- phase
- status
- idempotency key
- provider request id when available
- linked AI call log id when available
- retryable
- error code and message
- start and finish timestamps

For video decomposition, attempts are stored in `video_decomposition_attempt`. For AI video tasks, attempts are stored in `ai_video_task_attempt`.

## Retry Rules

Video decomposition retries are server-side state checked. Confirmed, successful, running, and non-failed episodes are rejected. Failed retryable episodes can be moved back to the requested pending phase while preserving successful sibling episodes and previous successful analysis/draft records.

AI video task polling does not expose a user retry API in this change. Failed poll attempts are recorded, and the existing `poll_retry_count` and `next_poll_at` rules continue to control automatic retry.

## Timeout Behavior

Claimed tasks carry `execution_timeout_at`. Timeout recovery marks stale video decomposition executions failed and retryable, clears the active execution token, and keeps attempt/log data available for inspection.

Schedulers should be safe to run repeatedly or from multiple instances because only claimed tasks enter the provider-call path.

## Diagnostics

When troubleshooting, inspect:

1. Current task or episode status.
2. `execution_token`, `execution_phase`, and `execution_timeout_at`.
3. Latest attempt row and its `idempotency_key`.
4. Linked `ai_call_log` row via `ai_call_log_id`.
5. Whether failure was provider transport, timeout, retry exhaustion, or business parsing failure.

Transport success does not imply business success. For video decomposition, a provider HTTP success can still become a failed attempt when the response cannot be normalized into the required script decomposition schema.

## Follow-up Workflows

Recommended next candidates for the same reliability component:

- AI image task execution.
- AI voice generation.
- Shot compose tasks.
- Episode compose tasks.

Each workflow should define its own legal phases and retryable states while reusing atomic claim, idempotency key, and attempt lifecycle helpers.
