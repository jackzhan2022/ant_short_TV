## Context

`ScopedAssetReextractionService` freezes the active episode set and Agent plan, then iterates all runnable units in one serial loop. Each unit performs a model conversation and formal asset save, so a 59-episode operation completes only one provider request at a time despite `ai.workflow-agent.fanout-concurrency` being configured. Normal script-analysis fan-out already demonstrates that episode children can execute concurrently while formal persistence resolves shared canonical identities.

Scoped re-extraction has additional semantics that must remain intact: one fenced execution owns the script, successful formal saves are recoverable after worker interruption, old assets are retired only after complete coverage, and `REGENERATE_ALL` applies only to the selected scope.

## Goals / Non-Goals

**Goals:**

- Run independent scoped re-extraction units concurrently up to the configured per-operation workflow fan-out bound.
- Preserve frozen source, model, Agent, Skill, scope, and prompt-policy inputs for every child.
- Isolate child failures, persist authoritative progress, and retain successful units across retries.
- Stop scheduling useful work after cancellation or execution-claim loss and reject stale writes.
- Keep finalization complete-only, idempotent, and ownership-fenced.

**Non-Goals:**

- Change the scoped re-extraction API or frontend workflow.
- Change provider credentials, model selection, billing rules, or prompt content.
- Add a new database schema or implement child-call-level cross-instance model quotas.
- Increase concurrency beyond the existing configured bound.

## Decisions

### Use a bounded executor inside scoped orchestration

The service will execute runnable episode units through a fixed-size executor capped by `ai.workflow-agent.fanout-concurrency` and the number of runnable units. Each worker will claim, execute, and persist one unit using the existing short transactions around network work.

This follows the established `EpisodeFanoutCoordinator` pattern while retaining the scoped snapshot tables and scoped finalizer. Directly delegating to `EpisodeFanoutCoordinator` was rejected because its store updates `script_analysis_*` parent state rather than `scoped_asset_reextraction_*` state. An unbounded executor or `parallelStream()` was rejected because neither provides an explicit operational concurrency contract.

### Keep unit failures isolated until aggregate validation

A normal child failure will mark only that unit failed and allow other children to finish. After all submitted work settles, the parent will derive counts from persisted rows and fail with `ANALYSIS_AGENT_INCOMPLETE` when any unit is incomplete. Execution-claim loss remains exceptional: it propagates after workers are interrupted because continuing under a stale owner is unsafe.

Fail-fast on every child error was rejected because it wastes already admitted independent work and leaves more units pending for the next paid attempt.

### Preserve database authority and fencing

Workers will revalidate execution ownership and source version before starting and before recording success. Unit state transitions remain transactional, and the formal save tool remains responsible for canonical identity, prompt-policy, variant, and binding concurrency safety. The executor will not hold a database transaction across a provider request.

### Finalize once after complete coverage

The parent thread will wait for all submitted children, recompute persisted progress, verify all frozen units have formal commit evidence, and then execute the existing scoped finalizer. No worker may retire old assets.

### Deploy before replacing the active operation

The current serial production operation will be canceled only after the new backend artifact is verified and active. A new equivalent `ALL + REGENERATE_ALL` request will then create or resume units under the new implementation. Previously committed unit evidence remains authoritative where the operation recovery contract permits reuse; no manual database status edits will be used.

## Risks / Trade-offs

- [Concurrent children contend on shared canonical assets] -> Keep all writes behind the existing formal save transaction, identity uniqueness rules, and ambiguity handling.
- [Provider capacity is lower than the configured bound] -> Keep the per-operation bound configurable; failed units remain retryable without discarding successes. A future child-call quota change must coordinate all workflow entry points rather than claim the current parent-task quota covers fan-out children.
- [Cancellation occurs with requests in flight] -> Stop new useful work, interrupt executor threads, and rely on ownership fencing to reject late stale writes.
- [One child failure delays the parent result until peers settle] -> This intentionally maximizes retained progress; persisted per-unit status remains visible while peers finish.
- [A service restart interrupts worker threads] -> Existing snapshot and commit-evidence recovery schedules only pending, failed, or interrupted units on the replacement attempt.

## Migration Plan

1. Add focused tests that demonstrate multiple scoped units overlap while respecting the configured bound, and that failures/cancellation preserve authoritative unit state.
2. Build and deploy the backend through the versioned release process, then verify `antv.service` and protected API health.
3. Cancel the currently active serial scoped re-extraction through the supported execution API/UI.
4. Submit a new `ALL + REGENERATE_ALL` operation and verify multiple units run concurrently, the new Skill revision is frozen, and progress advances without duplicate formal assets.
5. If verification fails, cancel the new operation, restore the previous release symlink, restart the service, and retain persisted snapshots for diagnosis.

## Open Questions

None. Production will use the existing configured per-operation fan-out value of 4. Cross-instance child-call quota enforcement is explicitly outside this change.
