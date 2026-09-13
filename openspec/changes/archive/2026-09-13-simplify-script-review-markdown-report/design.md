## Context

The current review path persists model findings through candidate JSON, semantic decisions, formal issues, hits, and lifecycle events. QUICK must call `save_review_result`; DEEP runs dimension children, semantic review, and aggregation before the same terminal tool can complete the task. This gives the workbench structured issue operations, but couples successful review completion to several model-facing schemas. Run #583 failed because a read tool returned `humanReviewFindings` while its declared output schema allowed only paged units.

The primary workflow is to read AI feedback, edit the script manually, and run another independent review. Existing structured reports must remain readable because they contain historical work. Task execution, access control, immutable version scope, AI usage, points, cancellation, retry, and audit logs remain system constraints.

## Goals / Non-Goals

**Goals:**

- Make non-empty Markdown text the only business result required for new reviews.
- Keep QUICK suitable for bounded scopes and DEEP suitable for long scripts through persisted units and aggregation.
- Remove model-facing candidate, semantic-decision, issue, and terminal-save schema dependencies from the new execution path.
- Preserve exact Markdown for viewing, copying, and download.
- Keep historical structured tasks readable and exportable without destructive migration.

**Non-Goals:**

- Automatically parse Markdown back into issues, severities, evidence locations, scores, or lifecycle states.
- Support per-issue resolution, batch repair, or cross-round matching for new Markdown tasks.
- Delete legacy tools, tables, or audit data in this change.
- Guarantee a fixed Markdown outline or factual correctness through server-side format validation.

## Decisions

### 1. Store Markdown separately and identify the result format

Add nullable `result_format` and `report_markdown` fields to `review_task`. New tasks created while the feature is enabled use `MARKDOWN`; existing rows with `result_json` and no format are interpreted as `STRUCTURED_JSON`. Add a Markdown result field to persisted DEEP unit output rather than overloading candidate JSON.

Keeping separate columns makes the API contract explicit and avoids disguising arbitrary text as JSON. Reusing `result_json` would require format guessing and would make historical reads fragile.

### 2. Treat model final text as the saved result

QUICK uses trusted backend scope construction and the existing model execution/accounting path, but does not require `save_review_result`. After a successful provider response, the adapter saves the final text in the same task-completion transaction.

The service rejects only null, empty, or whitespace-only output. It does not parse Markdown, enforce headings, inspect issue fields, or reject unknown content. Provider-reported truncation is a failed attempt: any partial text is retained for diagnosis, but the task is not marked complete.

This is simpler than introducing a `save_markdown_report` model tool, which would retain an unnecessary terminal-tool contract and another schema boundary.

### 3. Keep DEEP fan-out but replace structured stages with Markdown fragments

Each frozen DEEP unit produces one Markdown fragment and persists its run, attempt, status, error, and text. No candidate or semantic stage is scheduled. When every unit succeeds against the unchanged snapshot, the coordinator supplies the ordered fragments and frozen task context to one aggregation model call. Its final text becomes `review_task.report_markdown`.

Failed-unit retry reuses successful fragments. Aggregation retry reuses all fragments. Full regeneration creates a new attempt. Existing snapshot identity and cancellation guards remain in force.

Removing DEEP entirely was considered, but large scripts would exceed the safe model context. Keeping the current candidate pipeline and merely rendering its output as Markdown was rejected because it would retain the failure mode this change addresses.

### 4. Preserve trusted scope without terminal write tools

The backend continues deriving tenant, project, task, immutable version, range, dimensions, and attempt identity. QUICK and DEEP child runs may use bounded trusted context/content reads or receive equivalent server-built content. Aggregation receives only persisted fragments from the matching snapshot. New runs do not call candidate, semantic, history, or formal-result tools.

Legacy tool definitions remain registered for historical compatibility and rollback while the Markdown feature flag is active. They are excluded from new execution plans.

The Markdown path updates `script-review-foundation`, `script-review-execution-framework`, and `script-review-cross-episode-synthesis`. The foundation and execution framework stop requiring terminal save tools and define QUICK, DEEP child, and DEEP aggregation Markdown outputs. Cross-episode synthesis consumes ordered Markdown fragments and keeps content-level deduplication: findings with the same selected dimension, root cause, and stable source location are merged while preserving every distinct citation; similar wording with different root causes remains separate; duplicates caused by overlapping adjacent units appear once. Dimension Skills continue defining what to inspect and remain otherwise unchanged. `script-review-semantic-quality` stays available only for legacy structured attempts.

### 5. Select the workbench by result format

The task response adds `resultFormat` and `reportMarkdown`. A `MARKDOWN` task renders a report reader with copy and `.md` download actions. It does not render issue filters, issue detail, manual resolution, multi-hit navigation, or batch repair. A historical structured task continues through the current issue-oriented workbench.

Project-library state treats a completed Markdown report as completed review work rather than inferring action-required state from an empty issue array. Structured task state derivation remains unchanged.

### 6. Keep export deterministic

Markdown export returns the stored text byte-for-byte as UTF-8 with a `.md` filename. Other requested export formats can continue using existing conversion only if they consume the stored Markdown without reconstructing structured issues. Historical structured exports retain their existing assembly logic.

## Risks / Trade-offs

- [New reports lose issue filtering, exact highlighting, batch repair, and lifecycle tracking] → Show these actions only for historical structured tasks and make the Markdown report easy to copy and download.
- [Free-form output may omit a useful section or contain weak evidence] → Keep clear prompt guidance and review dimensions, while deliberately avoiding completion-blocking format validation.
- [Aggregation may lose detail from long unit fragments] → Preserve ordered fragments, use an aggregation prompt that prohibits dropping cited findings, and retain unit text for retry/audit.
- [Feature rollback creates two result formats] → Use explicit format discrimination and keep both readers; never infer Markdown from content syntax.
- [Partial provider output may be mistaken for a complete report] → Retain it for diagnosis but mark the task failed when the provider reports truncation.

## Migration Plan

1. Add nullable result-format and Markdown columns plus indexes needed by existing task reads; rehearse against existing structured rows.
2. Add dual-format backend responses and historical compatibility tests before changing execution.
3. Implement the Markdown QUICK path behind a disabled-by-default feature flag, then implement DEEP fragments and aggregation.
4. Add the Markdown workbench, library-state handling, and direct Markdown export.
5. Enable in a non-production environment and verify QUICK, long DEEP, failed-unit retry, aggregation retry, cancellation, truncation, usage settlement, and historical reads.
6. Enable for new production tasks. Existing in-flight structured tasks continue on their frozen execution path.

Rollback disables the feature flag for newly created tasks. Already completed Markdown tasks remain readable and exportable; no schema rollback or data deletion is required.

## Open Questions

None. The first release uses Markdown as the default for new tasks when the flag is enabled and keeps all legacy structured data read-only compatible.
