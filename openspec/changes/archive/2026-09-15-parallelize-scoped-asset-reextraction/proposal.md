## Why

Scoped asset re-extraction currently processes every frozen episode serially even though each episode is an independent Agent unit and the formal persistence layer already protects shared asset identities. A 59-episode `REGENERATE_ALL` operation therefore takes roughly an hour while the configured model capacity remains mostly idle.

## What Changes

- Execute scoped asset re-extraction episode units with bounded concurrency instead of a serial loop.
- Reuse the configured workflow fan-out limit so each scoped re-extraction has an explicit bounded concurrency.
- Persist unit claims and outcomes independently so a failed child does not discard successful episode results.
- Preserve frozen Agent, Skill, model, source-version, ownership-fencing, prompt-policy, and finalization guarantees.
- Make cancellation and execution-lease loss stop new unit scheduling while rejecting late writes from stale workers.
- Add regression coverage for concurrency bounds, partial failure, recovery, cancellation, and complete-only finalization.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `scoped-asset-reextraction`: Require bounded concurrent episode execution with persisted progress, failure isolation, and attempt-safe recovery.

## Impact

- Backend scoped re-extraction orchestration and its tests.
- Workflow fan-out configuration and execution-ownership coordination.
- Production deployment and restart procedure for the currently running serial operation.
- No API request or response shape changes and no database schema change are expected.
