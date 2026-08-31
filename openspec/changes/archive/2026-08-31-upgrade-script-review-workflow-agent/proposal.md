## Why

The independent script-review workbench already supports versioned drafts, configurable review tasks, structured issues, repair, export, and history, but its AI core is still one direct prompt invocation with static legacy Skills and no trusted tools. Upgrading it to a workflow Agent is needed so selected review dimensions load only their own rules, long deep reviews can make reliable per-unit progress, and formal issue writes are validated, atomic, auditable, and retryable.

## What Changes

- Replace the direct `script-review` text invocation with an enabled workflow Agent while preserving the independent `review_project` and `review_script_version` boundary.
- Add two common review Skills, thirteen dynamically selected dimension Skills, and one cross-episode synthesis Skill for deep-review aggregation.
- Register six trusted review tools for context, scoped content, issue history, unit-result persistence, unit-result reads, and final formal-result persistence.
- Keep quick review as one scoped Agent Run and implement deep review as bounded per-unit child Runs followed by one cross-episode aggregation Run.
- Persist deep-review snapshots, units, candidate results, child Run references, progress, failures, and targeted retries.
- Make the final save tool validate the frozen review version and scope, evidence anchors, selected dimensions, severity values, issue structure, multi-hit aggregation, and round matching before atomically writing the existing formal review tables.
- Preserve existing manual resolution, batch repair, version rollback, report history, export, unified AI execution, billing, cancellation, and authorization behavior.
- Correct scene-scoped review so `SCENES` limits model-visible content instead of falling back to the full script.

## Capabilities

### New Capabilities
- `script-review-workflow-agent`: Defines the formal review Agent, dynamic Skill composition, quick/deep Run contracts, required terminal saves, and audit snapshots.
- `script-review-agent-tools`: Defines trusted read and save tools, scope isolation, evidence validation, stale-source protection, and atomic formal-result persistence.
- `script-review-deep-fanout`: Defines frozen deep-review unit snapshots, bounded child Runs, persisted progress, failed-unit retry, and complete-only cross-episode aggregation.

### Modified Capabilities
- `script-review-workbench`: Changes review execution, scene scoping, progress, retry, and completion semantics while preserving the independent versioned workbench and its existing formal issue, repair, history, and export behavior.

## Impact

- Backend review orchestration, workflow-Agent runtime integration, tool catalog, scope guards, dynamic execution-plan composition, review matching and persistence services, and AI execution settlement.
- Additive database migrations for deep-review snapshots, units, candidate results, hashes, and Agent Run references; existing formal review tables remain authoritative.
- File-backed review Skills plus bootstrap and Agent-management visibility for the new `script-review` workflow Agent.
- Review APIs and frontend types/components gain per-unit progress, partial failure, stale state, current action, and targeted retry data without changing existing project/version/issue URLs unnecessarily.
- Tests expand across tool schemas, authorization, stale input, transaction rollback, Skill selection, quick review, deep fan-out, aggregation, retry, billing, and workbench rendering.
