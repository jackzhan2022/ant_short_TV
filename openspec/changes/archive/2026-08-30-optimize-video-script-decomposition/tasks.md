## 1. Persistence and compatibility foundation

- [x] 1.1 Add failing Flyway migration tests for an immutable one-result-per-episode table, tenant/batch lookup indexes, evidence references, content capacity, and preservation of all existing video decomposition and script-version columns.
- [x] 1.2 Add the Flyway migration for `video_decomposition_script_result` with tenant, batch, episode, analysis/call evidence, screenplay content, format version, timestamps, foreign keys, and a unique episode constraint.
- [x] 1.3 Add repository tests for inserting and reading an episode result, ordering batch results by episode number, rejecting duplicate episode results, and preventing update/delete through the repository API.
- [x] 1.4 Implement the result entity, mapper/repository, and immutable response model without changing historical draft or confirmation records.
- [x] 1.5 Add compatibility tests proving historical `PENDING_DRAFT`, `DRAFT_GENERATING`, `PENDING_REVIEW`, and `CONFIRMED` episodes and imported script-version links remain readable after migration.

## 2. Markdown screenplay protocol and prompt

- [x] 2.1 Add failing normalizer/validator tests for the outer `{"script":"..."}` protocol, Markdown episode heading, matching episode number, one or more consecutive scene headings, per-scene `出场人物：`, non-empty body, OS/VO cues, and `——本集完` marker.
- [x] 2.2 Add negative tests for invalid JSON, empty script, mismatched episode number, missing or skipped scene numbers, missing cast declaration, missing end marker, provider token-limit truncation, Markdown fences around the protocol, and oversized content.
- [x] 2.3 Implement a focused Markdown screenplay structure validator and integrate it with `VideoAnalysisNormalizer` while retaining raw-response diagnostics and normalized protocol JSON.
- [x] 2.4 Update the built-in video-understanding prompt and relevant Skills to emit the agreed Markdown shooting-script format, preserve video order and evidence, avoid quotation marks and `结尾钩子：`, avoid invented hooks, and return only the complete protocol object.
- [x] 2.5 Add prompt rendering and registry tests that lock the episode/scene template, dialogue/OS/VO conventions, evidence constraints, and protocol envelope.

## 3. Single-stage execution and immutable result saving

- [x] 3.1 Add execution service tests proving a valid direct screenplay is persisted once, links its analysis and AI call evidence, transitions the episode and shared execution to `SUCCEEDED`, and settles only the video-understanding stage.
- [x] 3.2 Update the new-episode success transaction to save the immutable screenplay result and transition directly from `ANALYZING` to `SUCCEEDED` without writing a new editable draft or `PENDING_REVIEW` state.
- [x] 3.3 Add tests proving new episodes never create `PENDING_DRAFT`, `DRAFT_GENERATION`, text-model calls, extra reservations, usage lines, or project `Script` / `ScriptVersion` records.
- [x] 3.4 Preserve and test the scheduler/executor branch that completes historical `PENDING_DRAFT` and `DRAFT_GENERATING` episodes without making that branch reachable from newly created batches.
- [x] 3.5 Add parser-failure tests proving invalid or truncated model output retains the raw response, marks the AI call as a business failure, records a retryable failed attempt, saves no result, and settles points according to the existing failure contract.

## 4. Retry and batch state machine

- [x] 4.1 Add service tests that allow retry only for `FAILED && retryable` episodes and reject retry/regeneration for pending, running, succeeded, pending-review, and confirmed states without issuing provider calls.
- [x] 4.2 Update retry handling so a technical retry creates a new attempt for the same execution version and preserves the frozen model, price versions, task scope, and successful sibling episodes.
- [x] 4.3 Remove the new-flow `DRAFT_GENERATION` retry option from request validation and user-facing APIs while retaining internal historical execution compatibility.
- [x] 4.4 Add batch aggregation tests covering all-pending, mixed pending/running, all-succeeded, all-failed, partial-failed, retry-in-progress, and historical review/confirmation batches.
- [x] 4.5 Implement centralized batch count, status, and percentage calculation with total, succeeded, failed, processing, and pending counts whose sum always equals the batch total.
- [x] 4.6 Expose episode execution phase and bounded percentage from persisted execution state, with succeeded results fixed at 100 and explicit error/retryability evidence for failures.

## 5. Batch and episode query APIs

- [x] 5.1 Add controller/service contract tests for batch list responses containing real counts, status, and percentage without loading screenplay LONGTEXT content.
- [x] 5.2 Add tenant-authorization tests for an all-screenplays batch endpoint that works for unbound batches, rejects cross-tenant access, and preserves project-bound historical access rules.
- [x] 5.3 Implement the all-screenplays query returning episodes ordered by `episodeNo`, immutable content for succeeded episodes, and status/progress/error/retryability for unfinished or failed episodes without persisting combined text.
- [x] 5.4 Update single-episode detail responses to expose immutable screenplay content and format version while retaining read-only historical draft, analysis, attempt, and imported-version evidence.
- [x] 5.5 Deprecate or disable draft update and confirmation mutations for new immutable results, with tests proving they cannot edit a result or create a project screenplay version.

## 6. Frontend service contracts and state mapping

- [x] 6.1 Add frontend service tests and types for new batch progress fields, episode percentages, immutable screenplay results, format version, and the ordered all-screenplays endpoint.
- [x] 6.2 Update handwritten video decomposition services and field/status dictionaries for `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, and `PARTIAL_FAILED` while retaining historical status labels.
- [x] 6.3 Add polling tests proving batch and open-detail data refresh while work is unfinished, stop at terminal state, and refresh correctly after an accepted failed-episode retry.

## 7. Video decomposition interface

- [x] 7.1 Run `npx antd info` for every Ant Design component newly introduced or materially changed in the batch progress and all-screenplays interface, and use the reported v6 APIs in the implementation.
- [x] 7.2 Update page tests to remove the top four-step `Steps`, overall `Progress`, `activeStep`, `overallPercent`, and the obsolete upload/review/import explanatory copy.
- [x] 7.3 Implement the simplified creation area and batch table with real overall percentage plus succeeded, processing, pending, and failed counts.
- [x] 7.4 Add component tests for opening “查看全部剧本” on running, succeeded, partially failed, and fully failed batches, including correct episode order and per-episode status rendering.
- [x] 7.5 Implement the on-demand all-screenplays view with Markdown rendering for succeeded episodes, collapsible per-episode sections, live progress for running episodes, and actionable failure details.
- [x] 7.6 Add tests and implement client-side “复制全部剧本” using currently succeeded episode content in episode order without calling a merge API or writing server state.
- [x] 7.7 Remove new-flow draft editing, save draft, regenerate draft, confirm import, project selection, and imported-version navigation controls; retain only read-only historical indicators where applicable.
- [x] 7.8 Add retry interaction tests and implement a retry button only for failed retryable episodes, including loading, success refresh, and normalized error feedback.

## 8. Verification and rollout readiness

- [x] 8.1 Run focused backend unit/integration tests for migration, format validation, execution persistence, retry, billing, compatibility, progress aggregation, and query authorization; fix all failures.
- [x] 8.2 Run focused frontend service/component tests for creation, progress polling, all-screenplays viewing, Markdown rendering, copying, historical display, and retry; fix all failures.
- [x] 8.3 Run `npm run tsc`, `npm run lint`, `npx antd lint ./src`, and the relevant backend Maven test suite, recording passing outputs and any explicitly unrelated pre-existing failures.
- [x] 8.4 Perform a smoke test with a multi-video batch covering concurrent progress, one retryable failure, technical retry, all-success completion, exact Markdown format, ordered all-screenplays viewing, and copy-all behavior.
- [x] 8.5 Verify through database and AI call logs that each new succeeded episode has exactly one immutable result and one billable video-understanding stage, with no text draft-generation or project screenplay writes.
