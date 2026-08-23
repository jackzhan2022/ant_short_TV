## Context

The project already has a unified AI invocation contract and a built-in `script-review` agent, but that capability is currently just a prompt-driven review action. The new requirement is an independent script review workbench with its own navigation entry, its own task lifecycle, version history, batch repair flow, and export surface.

The scripts to be reviewed can be large, multi-episode, and revised across multiple rounds. The design therefore has to preserve version immutability, support long-script review consistency, and keep review results traceable across rounds without allowing users to edit issue records directly.

## Goals / Non-Goals

**Goals:**
- Provide a dedicated script review workbench separate from the production workbench.
- Support imported `Word`, `TXT`, and `Markdown` scripts with immutable source retention.
- Support asynchronous review tasks with progress, cancellation, retry, and version locking.
- Support multi-round review, issue history, batch repair, rollback, and selected-version export.
- Keep issue numbering round-local while preserving cross-round comparison history.
- Make long-script review consistent by sharing one global review index across dimensions.

**Non-Goals:**
- Do not route review results into the production script workflow in this change.
- Do not add manual issue editing, issue merging, or cross-round manual relinking.
- Do not add multi-user collaboration or free-form comments.
- Do not require a full custom AI model; reuse the existing AI invocation contract and built-in review agent.

## Decisions

### 1. Build a separate review domain instead of extending the production workbench
The review experience will live under its own menu and route, with its own task page and version page. It will not be embedded into `/projects/:id/production-workbench/script`.

This keeps the review workflow from inheriting production assumptions such as script generation, storyboard, or element-confirmation flows. It also allows independent task management and export rules.

Alternative considered: reuse the production workbench with a hidden review tab. That would reduce routing work, but it would entangle unrelated workflows and make version locking harder to reason about.

### 2. Treat each reviewed script version as immutable and create a new version for every save
Each imported script becomes the first immutable version. Any edit produces a new version, and every review task binds to exactly one version. Once a task enters `RUNNING`, the selected dimensions, scope, and mode are locked.

This avoids the common failure mode where a review result drifts away from the underlying text. It also makes rollback natural: rollback is just switching the active working version back to a prior saved version.

Alternative considered: keep a mutable draft and record review snapshots separately. That would make UI simpler, but review traceability and export accuracy would be weaker.

### 3. Use a version-level global index plus scoped review passes for long scripts
For long scripts, the review engine will first build a global index for the active version: episode map, scene anchors, character graph, prop graph, timeline skeleton, and major plot beats. Then each selected dimension reviews against the same shared index.

This is the main consistency control. It prevents each block from inventing its own standard while still allowing the engine to review selected episodes or scenes. Quick review can stop at the scoped pass; deep review adds a full global pass plus round comparison.

Alternative considered: pure chunk-by-chunk review with independent prompts. That would be simpler, but it would make severity judgments drift across chunks and weaken cross-episode issue detection.

### 4. Model review output as issue-centric records with matched fragments and batch repair records
The persisted domain should separate:
- review task
- review round
- issue
- issue hit
- batch repair
- export snapshot

An issue is the stable unit shown to users. Each issue can aggregate multiple matched fragments. Batch repairs attach to issue hits instead of directly mutating issue records. Manual resolved markers are preserved as history, but a later review can move the issue back to `persists` if it still exists.

Alternative considered: store only raw AI JSON. That would be fast initially, but it would make version comparison, batching, and export brittle.

### 5. Reuse the existing AI invocation contract and built-in `script-review` scene
The workbench should call the existing review scene through the unified AI invocation layer, not through a separate transport path. The prompt can be extended to produce the richer issue schema, but the model routing, call logging, and error handling should stay centralized.

This preserves observability and reduces the chance of duplicating provider logic. The review domain should own the schema, not the provider adapter.

### 6. Keep cancellation and retry task-level, not issue-level
Task cancellation stops remaining stages but keeps completed stage results and call records. Retry starts a new task run for the same immutable input version and new selected configuration only if the task was not already running. Issue-level repair stays separate from task retry.

This avoids mixing user editing operations with background execution semantics.

## Risks / Trade-offs

- [Long-script review may miss cross-episode issues if the global index is weak] -> Build the global index first and make every dimension read from it before local review.
- [Issue matching across rounds may misclassify transformed problems] -> Prefer explicit anchors, entities, and problem types before semantic similarity; fall back to `uncertain` instead of forcing a match.
- [Batch replacement may change adjacent valid text] -> Require preview confirmation and keep rollback to the prior version.
- [Task cancellation may leave partial state] -> Persist completed stages, mark the task canceled, and prevent later stages from resuming implicitly.
- [A single schema may feel large for all review dimensions] -> Keep one common issue envelope and let dimension-specific logic live in `dimension` and `evidence`.

## Migration Plan

1. Add the review workbench data model and task lifecycle tables without wiring production workflows to them.
2. Expose the review task APIs, version APIs, issue aggregation APIs, batch repair APIs, rollback APIs, and export APIs.
3. Add the new menu and routes for review task management and review editing.
4. Integrate the review engine with the unified AI invocation layer and the built-in `script-review` agent.
5. Backfill only the new review domain; no existing production script data needs to be migrated into it automatically.
6. Rollback by disabling the new menu and review task creation paths while keeping stored review history intact.

## Open Questions

- Should deep review always run the global index pass before scoped review, or can very small scopes skip it?
- Should issue matching across rounds be persisted as hard links or recomputed on demand from version history?
- Should export include only the selected version's final review output or also all prior rounds in the same export package?
