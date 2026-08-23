## 1. Analysis Domain and Persistence

- [x] 1.1 Add database tables and indexes for script-version analysis tasks, stage records, structured results, progress fields, current action, retry metadata, and version ownership.
- [x] 1.2 Add backend entities, mappers, status enums, and response DTOs for analysis tasks and stage results.
- [x] 1.3 Add idempotent task creation keyed by project, script version, and analysis workflow.
- [x] 1.4 Add repository/service helpers that prevent results from an older script version from updating a newer version.

## 2. Four-Stage AI Workflow

- [x] 2.1 Add business scenes, prompt templates, variables, and output schemas for global story understanding, intelligent episode splitting, episode summary extraction, and character/scene recognition.
- [x] 2.2 Implement the analysis orchestrator with ordered stage transitions and persisted stage status changes.
- [x] 2.3 Implement global story understanding with structured output validation and AI call logging.
- [x] 2.4 Integrate `ScriptEpisodeParser` as the deterministic first path for explicit episode headings.
- [x] 2.5 Implement AI intelligent episode splitting for unstructured or insufficiently split scripts using story context and project settings.
- [x] 2.6 Validate intelligent split results for episode numbering, content coverage, duplicate boundaries, and required fields.
- [x] 2.7 Implement per-episode summary extraction with controlled concurrency and persisted summary results.
- [x] 2.8 Implement character, scene, and prop recognition with structured normalization and pending-review status.
- [x] 2.9 Record provider request IDs, durations, parsing failures, retryability, and sanitized raw responses for every stage.

## 3. Progress, Retry, and Retrieval APIs

- [x] 3.1 Add task detail and current-analysis query endpoints scoped by tenant, project, and script version.
- [x] 3.2 Expose stage status, percentage, completed units, total units, current action, result summary, and error details.
- [x] 3.3 Implement monotonic stage progress updates for measurable substeps and bounded estimates for model-only work.
- [x] 3.4 Set progress to 100 percent only after a valid result is persisted and recalculate weighted overall progress.
- [x] 3.5 Implement retry-from-failed-stage while preserving successful earlier stage results.
- [x] 3.6 Add explicit confirmation endpoints for recognized characters, scenes, and props using the existing asset confirmation workflow.

## 4. First Project Creation Integration

- [x] 4.1 Trigger idempotent analysis task creation only after successful first project creation with non-empty initial script content.
- [x] 4.2 Do not trigger analysis from ordinary later script saves; preserve a future explicit manual re-analysis boundary.
- [x] 4.3 Preserve existing project creation behavior when analysis task creation or scheduling fails, with an actionable non-blocking status.
- [x] 4.4 Extend script workspace responses with the active analysis task and intelligent episode result status without breaking existing clients.
- [x] 4.5 Add the future manual re-analysis API boundary for historical or later script versions without automatically analyzing existing data.

## 5. Frontend Progress and Review Experience

- [x] 5.1 Add frontend types and request helpers for analysis tasks, stage progress, results, retry, and confirmation.
- [x] 5.2 Keep the production workbench shell and script-page skeleton visible while project, script, or initial analysis data loads.
- [x] 5.3 Add a four-stage progress panel to the script workspace with fixed order, independent percentages, status colors, and current-action prompts.
- [x] 5.4 Add active-task polling that starts only for an unfinished initial analysis and cleans up on unmount or completion.
- [x] 5.5 Add intermediate result views for global understanding, episode splits, episode summaries, and recognized assets.
- [x] 5.6 Add failed-stage error display and retry controls without resetting successful stages.
- [x] 5.7 Add review and confirmation controls for characters, scenes, and props, including batch confirmation where supported.
- [x] 5.8 Add completed-analysis handling so revisiting the page displays results without restarting analysis.

## 6. Verification and Rollout

- [x] 6.1 Add backend tests for task creation idempotency, ordered stage transitions, version isolation, and failed-stage retry.
- [x] 6.2 Add backend tests for rule-based episode parsing, AI fallback splitting, structured validation, summary extraction, and asset normalization.
- [x] 6.3 Add API tests for progress snapshots, polling behavior, permissions, confirmation, and stale-version conflicts.
- [x] 6.4 Add frontend tests for four progress indicators, percentage rendering, current-action prompts, intermediate result access, retry, and polling cleanup.
- [x] 6.5 Run backend schema, unit, and controller tests plus frontend type, lint, and component tests.
- [x] 6.6 Document progress semantics, supported split inputs, retry behavior, model configuration, and operational failure handling.
