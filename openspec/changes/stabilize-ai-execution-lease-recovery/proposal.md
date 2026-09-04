## Why

Long-running per-episode analysis currently outlives the unified execution task's fixed ten-minute claim, so the dispatcher reclaims healthy work, starts a new attempt, and lets the stale attempt produce false unit failures or overwrite the parent analysis status. The current 58-episode asset-recognition run has reproduced this repeatedly, so lease renewal and stale-writer isolation are required before the workflow can complete reliably.

## What Changes

- Renew active unified-execution claims while a worker is still handling a task, using a bounded configurable heartbeat interval.
- Fence terminal and domain-state writes by the current execution attempt so a stale worker cannot overwrite a newer attempt.
- Preserve successful per-episode units while recovering interrupted, failed, or missing units into a later attempt.
- Allow one bounded model correction when an asset-save tool payload fails a correctable schema validation such as a missing `evidence` field; never synthesize evidence server-side.
- Align parent task, stage, execution, and fan-out snapshot status so clients do not see a terminal domain failure while a replacement attempt is actively recovering it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-task-execution-reliability`: Active long-running attempts renew their claims, and attempts that lose ownership are fenced from later terminal or domain writes.
- `script-analysis-agent-fanout`: Interrupted fan-out attempts preserve successful units, recover only unsuccessful units, and expose parent progress consistent with the active recovery attempt.
- `short-drama-asset-recognition-agent`: Correctable save-payload validation failures receive at most one evidence-preserving model correction before the unit fails.

## Impact

- Backend execution worker and claim lifecycle services.
- Script-analysis stage execution, fan-out coordination, and persisted parent progress.
- Workflow Agent tool-error loop for `save_episode_assets`.
- Execution and workflow-agent regression tests and production execution timing configuration.
- No public API shape, database schema, billing identity, or already successful episode result is removed or reset.
