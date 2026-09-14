## Context

Unified AI executions are claimed with a token and a ten-minute expiry. `AiExecutionClaimService` already supports heartbeat renewal and atomic token checks for execution terminal writes, but `AiExecutionWorker` never sends heartbeats while a handler is running. Per-episode analysis can legitimately run longer than ten minutes, especially when dozens of child Agent Runs are bounded by provider latency and fan-out concurrency.

When the dispatcher recovers an expired claim, the prior attempt is marked `TIMED_OUT` and a replacement attempt can start immediately. The prior Java call stack is not interrupted, however. Its child tool calls then fail the existing execution-scope guard, and its analysis-level exception handler can still write `FAILED` to the domain task after the replacement attempt has set it back to `RUNNING`. Fan-out snapshots already preserve successful units and recover interrupted `RUNNING` units as `STALE`.

The affected stakeholders are users watching script-analysis progress, operators diagnosing execution attempts, and billing/audit flows that require one trustworthy owner for every execution write.

## Goals / Non-Goals

**Goals:**

- Keep a healthy long-running execution claim alive until its handler exits.
- Ensure only the current attempt can publish terminal execution and script-analysis state.
- Reuse the current fan-out snapshot and rerun only unsuccessful units after interruption.
- Give asset recognition one bounded opportunity to correct a structurally invalid save payload using source-backed evidence.
- Make production configuration safe for long episode sets and concurrency four.

**Non-Goals:**

- Changing public API response shapes or database tables.
- Replacing the database-backed dispatcher with a message broker.
- Increasing per-unit model retries without a strict bound.
- Inventing, truncating, or server-generating evidence absent from the episode text.
- Regenerating successful episode units or changing frozen model and billing versions.

## Decisions

### Run lease renewal at the execution-worker boundary

`AiExecutionWorker` will open an `AutoCloseable` lease guard immediately after a successful claim and close it in `finally`. A shared scheduler will call `AiExecutionClaimService.heartbeat` at a configurable interval, defaulting to one minute. The guard records ownership loss when heartbeat returns false or throws, and the worker checks that state before publishing success or failure.

This boundary covers every long-running execution handler and keeps business coordinators unaware of claim timing. Renewing only inside `EpisodeFanoutCoordinator` was rejected because other long handlers would retain the same defect. Raising the timeout alone was rejected because it only moves the failure boundary.

### Fence domain failure publication by the active attempt

Script-analysis error handling will treat execution ownership loss as control flow, not as a business failure. Before persisting a stage/task failure for a unified execution, it will verify that the execution and attempt are still `RUNNING` and `STARTED`. A stale attempt will throw `AiExecutionClaimLostException` and skip `failTask` and stage failure writes.

The existing atomic claim-token checks remain authoritative for execution terminal state. The domain fence closes the separate race in which an expired handler writes to script-analysis tables after a replacement attempt starts.

### Preserve fan-out successes and recover only non-success units

The existing snapshot remains the source of truth. Opening a compatible prior snapshot converts interrupted `RUNNING` units to `STALE`; runnable selection remains limited to `PENDING`, `FAILED`, and `STALE`. `SUCCEEDED` units remain immutable for the recovery run. Parent progress is recomputed from unit rows whenever the replacement attempt opens or a unit finishes.

This reuses proven persistence behavior and avoids a new recovery table or full-regeneration path.

### Allow one tool-payload correction for asset recognition

The workflow loop will recognize a correctable `save_episode_assets` argument/schema failure. On the first such failure, it will append the structured tool error to the conversation and instruct the same run to call the tool again with only the invalid fields corrected from the trusted episode content. A run-local boolean prevents a second correction. A repeated failure terminates with `WORKFLOW_AGENT_TOOL_INVALID` or the original validation error.

This follows the existing bounded recovery patterns in `WorkflowAgentRunner` while preventing unlimited token use. Server-side evidence synthesis was rejected because evidence must be exact and auditable.

### Validate timing configuration

The claim timeout remains configurable and production will temporarily use 60 minutes as a safety margin. The heartbeat interval must be positive and less than the claim timeout; invalid configuration fails during component construction. Fan-out concurrency four becomes active on restart.

## Risks / Trade-offs

- **[Database heartbeat load]** One update per active execution per minute increases writes. → Use a shared scheduler, a one-minute default, and token-qualified single-row updates.
- **[Heartbeat thread survives handler completion]** A leaked schedule could extend dead work. → Always close the guard in `finally` and cover closure with a worker test.
- **[Transient heartbeat exception causes ownership ambiguity]** Continuing indefinitely could overlap workers. → Mark the guard lost on a failed renewal and prevent terminal/domain writes; dispatcher recovery remains authoritative.
- **[Replacement starts while stale provider call is still in flight]** External cancellation is not always possible. → Fence all local writes and preserve provider-call audit records; do not present the stale result as successful.
- **[Correction increases tokens]** A repair turn costs more than immediate failure. → Permit exactly one correction only for the asset-save payload and expose the final error if it remains invalid.
- **[Restart interrupts the current run]** Deployment creates temporary `STALE` units. → Reuse the compatible snapshot and retry only non-success units after health checks.

## Migration Plan

1. Add and run focused lease-guard, stale-attempt, fan-out recovery, and tool-correction tests.
2. Deploy the backend with `AI_EXECUTION_HEARTBEAT_INTERVAL=PT1M`, `AI_EXECUTION_CLAIM_TIMEOUT=PT60M`, and the existing fan-out concurrency value of four.
3. Restart `antv.service`, verify the active release and authenticated/unauthenticated health endpoints, and confirm heartbeat timestamps advance for a running execution.
4. Retry the failed script-analysis stage. The existing snapshot retains successful units and schedules only `FAILED` or `STALE` units.
5. Verify all 58 units, fan-out snapshot, analysis stage, domain task, and unified execution reach successful terminal states.
6. Roll back by restoring the previous release and environment backup. The previous binary ignores the new heartbeat interval variable, and no schema rollback is required.

## Open Questions

None. The existing claim token, attempt identity, snapshot format, and tool-error conversation format are sufficient for this change.
