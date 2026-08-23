## 1. Data Model and Persistence

- [x] 1.1 Add database tables for review projects, immutable script versions, review tasks, review rounds, issues, issue hits, batch repairs, and export records.
- [x] 1.2 Add entity, mapper, enum, and response classes for the review domain and wire them into the backend module structure.
- [x] 1.3 Add version-locking and idempotency helpers so a running task always stays bound to one immutable script version and duplicate task creation is prevented.
- [x] 1.4 Add rollback helpers that restore a prior saved version without deleting historical versions or review history.

## 2. Review Engine and AI Integration

- [x] 2.1 Extend the built-in `script-review` agent prompt and output schema to return review rounds, issue numbers, dimensions, locations, evidence, suggestions, and statuses.
- [x] 2.2 Add a global review indexing pass for multi-episode scripts to capture characters, scenes, props, timeline, and major plot beats before dimension review runs.
- [x] 2.3 Implement quick-review and deep-review execution paths with the same shared review index and dimension-specific checks.
- [x] 2.4 Add issue matching across rounds so the engine can classify `new`, `persists`, `fixed`, `shifted`, and `uncertain` results.
- [x] 2.5 Add AI call logging for review tasks, including model, duration, request ID, failure reason, and sanitized structured output.

## 3. Task Management APIs

- [x] 3.1 Add APIs to create review projects from imported `Word`, `TXT`, and `Markdown` content and to create new script versions on save.
- [x] 3.2 Add task-management APIs to list review tasks, fetch task progress, cancel waiting or running tasks, and retry failed tasks.
- [x] 3.3 Add APIs to lock task configuration once a task enters running state and to reject edits to a running task's dimensions, scope, or mode.
- [x] 3.4 Add APIs to mark issues as manually resolved, move them into the processed area, and preserve the manual marker history.
- [x] 3.5 Add APIs to fetch version history, version diff data, issue mappings, and round history for a selected script version.

## 4. Review Workspace UI

- [x] 4.1 Add a new independent menu entry and route for the script review workbench.
- [x] 4.2 Build the review task management page with task creation, progress display, cancellation, retry, and version-level navigation.
- [x] 4.3 Build the review editor page with script import, version history, current-version locking, and save-as-new-version flow.
- [x] 4.4 Build the dimension picker, scope picker, and quick/deep mode selector for new review tasks.
- [x] 4.5 Add review result panels that show overall conclusion, score, issue counts, processed issues, and history comparison.

## 5. Issue Navigation and Batch Repair

- [x] 5.1 Build issue cards with issue number, severity, dimension, status, location, excerpt, evidence, and suggestion.
- [x] 5.2 Build issue-hit lists so one issue can map to multiple matched fragments and the user can select hits for bulk repair.
- [x] 5.3 Build the editor-to-issue linking flow that scrolls to the matched fragment and highlights the selected text.
- [x] 5.4 Build batch repair preview and confirmation modals for global replace, batch insert, and batch delete.
- [x] 5.5 Wire batch repair actions to save a new version, record the applied hits, and keep a rollback path to the previous version.
- [x] 5.6 Add the processed-issue area so manually resolved issues collapse out of the default issue list but remain searchable in history.

## 6. Export, Tests, and Documentation

- [x] 6.1 Add export flows for selected script versions, including task number, round number, issue mappings, and comparison data.
- [x] 6.2 Add backend tests for version immutability, task idempotency, cancellation, retry, issue matching, manual resolved markers, and rollback.
- [x] 6.3 Add backend tests for multi-episode indexing, quick-review vs deep-review behavior, and structured `script-review` output validation.
- [x] 6.4 Add frontend tests for the review task page, dimension picker, scope locking, issue-hit selection, batch repair preview, and processed-issue area.
- [x] 6.5 Add export and version-history tests to verify selected-version output and round-to-issue mapping.
- [x] 6.6 Document the review workflow, task states, version rules, batch repair rules, and export semantics for operators and maintainers.
