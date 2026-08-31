## Context

The review workbench is intentionally independent from the short-drama creation workspace. It owns `review_project`, immutable `review_script_version` rows, asynchronous `review_task` rows, formal issues and hits, issue history, repair records, exports, and rollback. Today `ReviewWorkbenchService` scopes content, builds a small index, injects all inputs into the legacy built-in `script-review` prompt, performs one `AiInvocationService.invokeText` call for both QUICK and DEEP, parses JSON, and writes formal issue rows directly.

That path already has authorization, version history, unified AI execution, point reservation and settlement, cancellation, whole-task retry, repair, and export. The change must preserve those contracts while introducing the workflow-Agent guarantees already used by the short-drama analysis pipeline: frozen Agent and Skill revisions, explicit tools, trusted scope, required terminal saves, audited model/tool steps, stale-source rejection, persisted fan-out progress, and failed-unit retry.

Review input can be imported Word, TXT, or Markdown and is not guaranteed to contain explicit episode headings. A review can select one or more of thirteen dimensions and scope ALL, EPISODES, or SCENES. Formal review issues remain user-editable workflow data and must not be replaced by intermediate model candidates.

## Goals / Non-Goals

**Goals:**

- Run review through an enabled workflow Agent identified by `script-review` and expose it in Agent (New) management.
- Load only the common review Skills plus the dimension Skills selected by the trusted review task.
- Register six trusted review tools with phase-specific allowlists and server-derived business scope.
- Keep QUICK as one bounded Agent Run and make DEEP a persisted per-unit fan-out followed by one cross-unit aggregation Run.
- Write final results atomically into the existing formal review tables after deterministic validation and round matching.
- Restore progress after reload, retry only unsuccessful deep-review units, and prevent incomplete or stale snapshots from finalizing.
- Preserve current APIs and user workflows wherever their semantics remain valid.

**Non-Goals:**

- Moving review projects into `script`, `script_version`, or `script_episode` tables.
- Letting the review Agent edit scripts, apply repairs, export reports, resolve issues, or roll back versions.
- Creating thirteen public Agents or one Agent per dimension.
- Replacing existing review history, repair, export, authorization, billing, or immutable-version behavior.
- Automatically treating an AI candidate result as formal before the terminal save tool succeeds.

## Decisions

### 1. Preserve the workbench boundary and replace only the AI execution core

`review_project` and `review_script_version` remain the trusted review domain. Existing formal tables remain authoritative:

- `review_task`
- `review_issue`
- `review_issue_hit`
- `review_issue_event`
- `review_batch_repair`
- `review_export_record`

The direct built-in prompt invocation is retired behind a rollout flag and replaced by `WorkflowAgentRunner`. Existing project, version, task, issue, repair, history, rollback, and export endpoints remain stable.

Alternative considered: reuse `read_current_script` and the short-drama creation tables. Rejected because an imported review draft can be unbound or differ from a main-project script, and reusing the creation scope would violate authorization and version identity.

### 2. Use one public Agent with trusted dynamic Skill composition

The public Agent code remains `script-review`. Its stored definition contains the maximum approved Skill and tool set. A server-side execution-plan factory reads the immutable task configuration and produces a narrower frozen plan; the client and model cannot provide Skill codes.

Every Run loads, in order:

1. `script-review-foundation`
2. `script-review-execution-framework`
3. one Skill for each selected dimension, using a fixed enum-to-Skill mapping

The thirteen dimension Skills cover plot logic, dialogue, character relationship, character knowledge, character motivation, timeline, scene continuity, prop continuity, visual continuity, shootability, emotion, suspense/reversal, and foreshadowing. A deep aggregation Run additionally loads `script-review-cross-episode-synthesis`.

Legacy `strict-json-output` and `review-json-output` are not loaded because tool schemas own the machine contract. Legacy `no-invention` becomes part of the review foundation, and `script-review-rules` is replaced by the dimension Skills.

Alternative considered: one large rubric Skill. Rejected because single-dimension reviews would pay for and be influenced by twelve unrelated rubrics. Alternative considered: thirteen Agents. Rejected because model, permissions, lifecycle, tools, and administration are shared.

### 3. Register six tools and enforce phase-specific allowlists

The global tool catalog registers:

- `read_review_context`
- `read_review_content`
- `read_review_issue_history`
- `save_review_unit_result`
- `read_review_unit_results`
- `save_review_result`

QUICK exposes the three reads plus `save_review_result`. A DEEP child exposes context, content, history, and `save_review_unit_result`. A DEEP aggregation Run exposes context, history, unit-result reads, and `save_review_result`.

Tools derive tenant, review project, version, task, unit, phase, user, and permissions from `ToolExecutionContext`. Model arguments never contain business IDs or arbitrary Skill codes. The scope guard rejects a tool outside the frozen phase even if it exists in the Agent's maximum allowlist.

### 4. Make trusted reads bounded and position-aware

`read_review_context` returns task mode, selected dimensions, scope, round, immutable version hash, structural counts, required coverage, and safe opaque snapshot keys. `read_review_content` returns only content inside the frozen scope with stable unit, episode, scene, line, character-offset, and anchor metadata. `SCENES` is implemented as an actual server-side filter rather than falling through to the full script. `read_review_issue_history` returns bounded prior-round issue summaries and hit anchors relevant to the selected scope and dimensions.

For QUICK, the server estimates the complete composed request. If the selected scope cannot fit the safe model budget, it fails with `REVIEW_SCOPE_TOO_LARGE_FOR_QUICK` and directs the user to narrow the scope or select DEEP; it does not silently claim partial coverage.

For heading-free DEEP input, a deterministic planner creates stable bounded units from scene headings, paragraph boundaries, and character offsets. These are review units only and do not become formal episodes.

### 5. Separate candidate persistence from formal result persistence

`save_review_unit_result` stores bounded candidate issues, coverage, content hash, selected dimensions, and child Run identity in additive deep-review tables. Unit candidates are never returned as the formal report.

`save_review_result` is the only terminal formal save. In one transaction it:

- locks the task and verifies mode, phase, immutable version hash, scope hash, and selected dimensions;
- for DEEP, verifies every frozen unit succeeded and was generated from the same snapshot and frozen plan;
- validates score, conclusion, dimension, severity, issue fields, locations, evidence, and multi-hit structure;
- verifies excerpts and anchors occur in the permitted frozen content;
- rejects duplicate or ambiguous issue payloads;
- deterministically matches the previous round and computes `new`, `persists`, `shifted`, `fixed`, or `uncertain` while preserving manual-resolution history;
- replaces no prior round and inserts the new round's formal issues, hits, and events atomically;
- stores a bounded diagnostic result and terminal Agent Run reference on the task.

The Agent can attempt corrections after validation errors, but each Run can have only one successful terminal save. Final text without the required save fails the Run.

### 6. Implement DEEP as a frozen fan-out plus aggregation

Add:

- `review_fanout_snapshot`
- `review_fanout_unit`
- `review_unit_result`

The snapshot freezes version hash, scope hash, unit-set hash, Agent revision, Skill revisions, model, selected dimensions, attempt, and concurrency policy. Child Runs are bounded by configurable concurrency and each receives one trusted unit. Progress is reconstructed from persisted units and candidate-save coverage.

Only FAILED, MISSING, or STALE units are scheduled on retry unless full regeneration is explicitly requested. Successful units are reused only when version, scope, unit fingerprint, selected dimensions, Agent revision, Skill revisions, and model still match. Once every unit succeeds, one aggregation Run reads all candidates and performs cross-unit deduplication and continuity analysis before the final save.

Alternative considered: keep DEEP as one large model request. Rejected because it cannot reliably cover long scripts, restore per-unit progress, or retry one failed section.

### 7. Keep deterministic responsibilities in backend code

Skills define review judgment and evidence standards. Tools define schemas and trusted reads/writes. Backend services, not the model, own authorization, hashing, scope filtering, exact excerpt checks, idempotency, issue numbering, prior-round matching, transaction boundaries, progress calculation, cancellation, and settlement.

The existing `ReviewIssueMatcher` is expanded from a simple signature comparison into deterministic candidate matching with stable anchors, normalized dimensions, multiple hits, ambiguity handling, and explicit uncertain outcomes. Model-provided issue numbers and lifecycle statuses are advisory and are not trusted as database identity.

### 8. Correlate child calls with the existing execution and billing model

One user-visible `review_task` keeps one parent unified AI execution. All workflow Agent Runs and model calls carry the task, execution, attempt, phase, and snapshot correlation. Reservation estimates account for QUICK versus DEEP unit and aggregation call ceilings; final settlement uses aggregated actual usage. Cancellation stops pending scheduling and prevents finalization, while already audited provider calls remain recorded.

### 9. Expose persisted progress without breaking current workbench APIs

Task responses add Agent Run references, frozen mode, current unit, total/completed/failed units, unit statuses, retryability, stale state, and aggregation status. Existing overall progress and current action remain populated for compatibility. The UI renders QUICK as one Run and DEEP as unit progress plus aggregation, with targeted retry and explicit overwrite/regeneration warnings.

## Risks / Trade-offs

- [Many small dimension Skills can drift] → Use a shared template, behavior fixtures, stable dimension enum mapping, and contract tests that compare frontend and backend catalogs.
- [Dynamic plan composition could expose unselected Skills or tools] → Build plans only from server-owned mappings, freeze them before the first model call, and verify snapshots in Run audit tests.
- [Heading-free scripts produce imperfect units] → Keep units review-only, use stable character offsets and overlap, and require aggregation before formal conclusions.
- [Cross-unit aggregation can duplicate or contradict candidates] → Persist opaque candidate keys and anchors, deduplicate deterministically before final save, and reject ambiguous merges.
- [Strict evidence validation can reject useful paraphrases] → Require at least one exact excerpt or trusted anchor per issue while allowing the problem and suggestion text to be interpretive.
- [Deep review costs more calls] → Bound concurrency and units, estimate reservation before scheduling, reuse matching successful units, and show the mode/cost implication before start.
- [Legacy and workflow paths could both run] → Gate dispatch through one feature flag, enforce one idempotency key per task execution version, and monitor duplicate Run/result metrics.
- [Existing consumers expect only overall progress] → Add fields compatibly and continue populating the legacy progress fields from persisted workflow state.

## Migration Plan

1. Add fan-out, unit-result, hash, Agent Run reference, and index migrations without altering existing formal issue data.
2. Register tools and install Skills while the workflow dispatch flag remains disabled.
3. Bootstrap the disabled `script-review` workflow Agent and verify its model supports tool calling.
4. Backfill no historical candidates; existing tasks and reports remain readable as legacy executions.
5. Enable QUICK for non-production tasks, compare formal output and billing with the legacy path, then enable production QUICK.
6. Enable DEEP child fan-out, targeted retry, and aggregation after progress and settlement smoke tests.
7. Remove direct dispatch only after all active legacy tasks finish; retain compatibility reads for their result JSON and call logs.

Rollback disables workflow dispatch and restores direct invocation for new tasks. Additive tables and columns remain dormant, existing formal reports remain valid, and no down migration is required during operational rollback.

## Open Questions

None. The agreed design uses one public review Agent, dynamic dimension Skills, six registered tools, one-Run QUICK, and fan-out-plus-aggregation DEEP while preserving the independent workbench.
