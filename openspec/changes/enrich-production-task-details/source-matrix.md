# Production Task Detail Source Matrix

All rows are read-only. A task being visible is not sufficient to read content: the caller must also pass the listed domain permission and every referenced row must match the tenant and task/version/run anchor.

| Task type | Saved submission/settings | Result anchor | Required content permission | Missing or mutable boundary |
|---|---|---|---|---|
| IMAGE | `ai_image_task.prompt`, `negative_prompt`, `reference_images`, model and generation columns | `ai_image_result.task_id` | `AI_IMAGE_TASK:VIEW`; downloads keep the image domain download guard | Missing prompt is `NOT_RECORDED`; results from another task are never substituted; `is_selected` is labelled as current state |
| VIDEO | `ai_video_task.prompt`, first/last frame, references and generation columns | `ai_video_result.task_id` | `AI_VIDEO_TASK:VIEW`; video download keeps the video domain guard | Missing frames are omitted; missing result is `PENDING`; no arbitrary stored URL proxy |
| SCRIPT_ANALYSIS | `script_analysis_task.script_version_id` plus workflow/stage configuration | `script_analysis_stage.task_id` and `script_analysis_result.task_id` | `SCRIPT:VIEW` | Missing version/execution remains `NOT_RECORDED`; normalized/provider JSON and raw errors are excluded |
| REVIEW | `review_task.script_version_id` to `review_script_version`, mode, scope and dimensions | Selected task's `report_markdown` | Main-project permission when bound; otherwise original review creator/source guard | Never use a newer review round; retired `review_issue` rows are not queried; missing report is `PENDING` or `NOT_RECORDED` according to task state |
| VIDEO_DECOMPOSITION | Batch name/model and recorded episode rows | `video_decomposition_episode.batch_id` aggregate/page | Existing decomposition batch view permission | Empty batch is valid; child bodies are not loaded with the batch page |
| VIDEO_EPISODE | Episode-owned source filename, MIME type, size, duration and versions | `video_decomposition_script_result.episode_id`, then recorded draft fallback | Existing decomposition episode view permission | Supports historical rows without `execution_id`; never reads another episode's result |
| STORYBOARD_BATCH | Batch name/script and recorded batch items | `storyboard_batch_item.batch_id` | `STORYBOARD:VIEW` | Batch submitter is independent from execution origin; an empty batch has no fabricated output |
| STORYBOARD_ITEM | Batch item episode and execution references | Only a run proven to belong to `storyboard_batch_item.execution_id` | `STORYBOARD:VIEW` plus source execution input permission | Reused execution grants no access to the original submitter's private input and no control rights |
| SCRIPT_OPERATION / SCRIPT_GENERATE | Fixed `script_version_id`; saved request JSON is redacted and is not represented as verbatim input | `result_type=SCRIPT_VERSION`, `result_id=script_version.id` | `SCRIPT:VIEW` | Missing request text is `NOT_RECORDED`; current script is never substituted |
| SCRIPT_OPERATION / SCRIPT_REWRITE | Fixed source `script_version_id`; redacted request may provide only safe scope metadata | `result_type=SCRIPT_VERSION`, `result_id=script_version.id` | `SCRIPT:VIEW` | Source and output remain separate; later script versions do not replace either |
| SCRIPT_OPERATION / ELEMENT_EXTRACT | Fixed source version and verifiable saved scope only | Existing task/run-linked extraction records only | `SCRIPT:VIEW` for source; `ELEMENT:VIEW` for categorized results | Current project assets are not historical evidence; absent run linkage is `NOT_RECORDED` |
| SCRIPT_OPERATION / SCOPED_ASSET_REEXTRACTION | Fixed source and `scoped_asset_reextraction_snapshot.operation_id` scope/plan | Snapshot units and run-linked assets/variants | `SCRIPT:VIEW` plus `ELEMENT:VIEW` for result resources | Superseded mutable assets are unavailable unless their run/snapshot ownership remains provable |
| SCRIPT_OPERATION / STORYBOARD_BREAKDOWN | Fixed script/episode input where recorded | operation `execution_id` -> `ai_call_log` -> run step -> `storyboard.generated_by_run_id` | `SCRIPT:VIEW` and `STORYBOARD:VIEW` for results | Multiple run steps must not duplicate shots; current episode storyboards are never used as fallback |
| SCRIPT_OPERATION / PROMPT_GENERATE | Fixed target/scope only when safely recoverable from saved input | `SCRIPT_PROMPTS` identifies a project, not immutable prompt rows | `SCRIPT:VIEW` and target asset/storyboard permission | Project-level result id alone cannot prove historical prompt values, so mutable prompts are `NOT_RECORDED` |

## Cross-cutting boundaries

- A team OWNER or active system ADMIN may see team task summaries but gains no project, draft, execution-input, media or download permission.
- Unbound tasks are private to their creator unless the source domain has an explicit independent sharing rule. An unbound colleague task remains unreadable.
- A shared storyboard execution keeps two identities: batch submitter controls batch visibility, while execution origin controls private execution input. Reuse grants neither private input nor cancel/retry rights.
- Historical tasks without a fixed version, task id, episode id, snapshot id or execution/run chain return `NOT_RECORDED`; current project state is never used to reconstruct history.
- Missing media references are `NOT_RECORDED`, unfinished output is `PENDING`, known deleted resources are `DELETED`, failed authorization is `RESTRICTED`, and unknown historical subtypes are `UNSUPPORTED`.
- Detail reads do not add migrations, snapshots, write hooks, background backfills, media ingestion, AI calls, billing calls or production transactions.

## User Acceptance Record

| User-visible scenario | Evidence | Expected historical boundary |
|---|---|---|
| Saved image and video task | Task-owned media rows, bounded previews and result pages | A missing or deleted media reference does not hide saved settings or other results. |
| Script generation and rewrite | Fixed `script_version_id` and result version ID | Input and output stay tied to their recorded versions after later edits. |
| Asset extraction and scoped re-extraction | Operation/run anchor or scoped snapshot | Current project assets are never used as an historical substitute. |
| Storyboard operation and batch item | Execution-to-run-to-storyboard chain | Shared execution shows only output proved by the execution; unrecorded episode source text is `NOT_RECORDED`. |
| Prompt generation | Saved target type and target ID | Mutable current prompts are not presented as the task result. |
| Review and video decomposition | Task-specific draft/report or episode result rows | Another review round or episode cannot satisfy a selected task detail. |
| Access revocation | Current membership plus domain permission checks on every detail read | The next request removes or restricts prior content; list summaries remain lightweight. |
