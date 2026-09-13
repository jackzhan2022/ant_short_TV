> 2026-09-12 替代声明：`remove-legacy-ai-workflow-paths` 是最终目标。历史结构化展示、结果格式切换、旧表/工具兼容及开关回滚条款已被替代；QUICK/DEEP 仅保留 Markdown。 原勾选和验证记录为历史证据，不据此恢复旧链路。归档时先合并仍有效的基础能力，最后合并 remove-legacy-ai-workflow-paths；之后不得再导入这些已退役条款。

## 1. Persistence and compatibility

- [x] 1.1 Add a database migration for nullable task `result_format` and `report_markdown` fields and persisted DEEP unit Markdown fragments, preserving all existing structured rows.
- [x] 1.2 Extend review task and unit entities, mappers, and response contracts with explicit result format and Markdown content.
- [x] 1.3 Add migration and repository tests proving old structured tasks remain readable and new Markdown text is stored without transformation.

## 2. Markdown QUICK execution

- [x] 2.1 Add the Markdown review feature flag and stamp the selected result format when a new task and execution attempt are frozen.
- [x] 2.2 Update `script-review-foundation` and `script-review-execution-framework` plus the QUICK execution plan so trusted scope and selected dimensions produce final Markdown without candidate, history, semantic, or terminal-save tools.
- [x] 2.3 Persist non-empty QUICK final text through the server-owned completion transaction while recording model usage, points, run identity, and failure diagnostics.
- [x] 2.4 Add QUICK tests for arbitrary Markdown, unknown headings and tables, whitespace-only output, provider-reported truncation, cancellation, retry, and absence of structured issue writes.

## 3. Markdown DEEP execution

- [x] 3.1 Replace DEEP candidate persistence with one non-empty Markdown fragment per frozen unit while retaining hashes, attempts, progress, errors, and run correlation.
- [x] 3.2 Update `script-review-execution-framework` and `script-review-cross-episode-synthesis`, remove semantic-quality and anomaly-gate scheduling from new Markdown attempts, and exclude legacy candidate/write tools from their execution plans.
- [x] 3.3 Aggregate ordered successful fragments in one model call, merge duplicates by dimension, root cause, and stable source location while preserving distinct citations, and persist the non-empty final Markdown without structured-result validation.
- [x] 3.4 Preserve failed-unit-only retry, aggregation-only retry, full regeneration, stale-snapshot detection, cancellation, and idempotent usage settlement for Markdown attempts.
- [x] 3.5 Add coordinator tests for complete success, one-unit failure and retry, aggregation failure and retry, empty or truncated output, stale input, cancellation, and no candidate/decision/issue writes.

## 4. API, export, and project state

- [x] 4.1 Return `resultFormat` and `reportMarkdown` from task and history APIs while retaining existing summary and issue payloads for structured tasks.
- [x] 4.2 Make project-library state treat a completed Markdown report as completed work instead of deriving its status from an empty issue list.
- [x] 4.3 Export stored Markdown directly as UTF-8 `.md` content and preserve existing structured export behavior.
- [x] 4.4 Add controller tests for dual-format task reads, project summary state, version binding, access control, and byte-for-byte Markdown export.

## 5. Workbench experience

- [x] 5.1 Extend frontend review types and loading state to discriminate Markdown tasks from historical structured tasks.
- [x] 5.2 Add a scrollable Markdown report reader with copy and `.md` download actions for completed Markdown tasks.
- [x] 5.3 Hide issue filters, issue detail, hit navigation, manual resolution, and batch repair for Markdown tasks while leaving the historical structured workbench unchanged.
- [x] 5.4 Update library status labels and counts for Markdown tasks and add frontend tests for switching between result formats, failed tasks, copy, and download.

## 6. Rollout and verification

- [x] 6.1 Document feature enablement, in-flight structured-task behavior, rollback, and the retained legacy tool/table compatibility boundary.
- [x] 6.2 Rehearse the migration against a database containing structured reports and verify no historical task, issue, hit, event, candidate, or semantic-decision data changes.
- [x] 6.3 Run backend review and workflow tests, frontend tests, TypeScript, Biome, Ant Design lint, and production builds.
- [x] 6.4 Verify QUICK, long-script DEEP, failed-unit retry, aggregation retry, cancellation, AI usage and point settlement, Markdown viewing/export, and historical structured viewing in a non-production environment before enabling the feature for new tasks.
