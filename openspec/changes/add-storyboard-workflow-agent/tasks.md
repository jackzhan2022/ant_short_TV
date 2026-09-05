## 1. Formal storyboard data model

- [x] 1.1 Add a Flyway migration that introduces `episode_id`, `storyboard_no`, `shot_plan_json`, `prompt_document_json`, `source_fingerprint`, `generated_by_run_id`, and material-binding status on `storyboard`, backfills legacy ordering, and adds active episode/order and Run indexes without deleting legacy columns.
- [x] 1.2 Extend the storyboard entity, mapper, workspace response, save request, and frontend service types to expose authoritative storyboard number, internal shot plan, prompt document, rounded duration, material status, and generating Run while preserving legacy response compatibility.
- [x] 1.3 Add schema migration tests proving legacy storyboard rows remain readable and new columns/indexes support episode-scoped formal data.

## 2. Storyboard Skills and Agent registration

- [x] 2.1 Add failing repository/bootstrap tests for an enabled `short-drama-storyboard` Workflow Agent using the project TEXT model and the required ordered Skill and tool lists.
- [x] 2.2 Create the `short-drama-storyboard-planning` Skill covering whole-episode ordering, 10–15 second storyboard groups, 1.5–4 second internal shots, one primary action per shot, adjacent-space grouping, exact spoken-content preservation, and bounded visual elaboration.
- [x] 2.3 Create the storyboard material-reference Skill covering actual-use-only references, stable asset keys, project/material priority, no repeated bound-asset appearance descriptions, and unmatched-name behavior.
- [x] 2.4 Create the Seedance video-prompt Skill with the confirmed prompt structures and example motion/adverb dictionary, explicitly allowing clear natural-language extensions rather than a closed vocabulary.
- [x] 2.5 Implement the Agent Bootstrap and configuration switch with the ordered Skills, project TEXT model compatibility, bounded temperature/token/step settings, and the six approved tools.

## 3. Trusted read-tool context

- [x] 3.1 Add tests that scope all storyboard reads to the trusted tenant, project, script, and active episode and reject foreign, retired, or missing episode scope.
- [x] 3.2 Extend `read_current_episode` state capture so the storyboard save can verify the exact episode ID, script ID, content fingerprint, and source content read by the Run.
- [x] 3.3 Provide the storyboard Agent with the previous ending summary and next opening summary through `read_adjacent_episodes` without using adjacent full bodies as planning context, including null boundary behavior.
- [x] 3.4 Extend `read_script_analysis` to return the current formal global-understanding document needed by storyboard planning rather than relying on historical raw stage output.
- [x] 3.5 Extend `read_script_assets` to return stable asset keys, aliases, visual variant keys, current-episode bindings, and project-primary selections with deterministic size limits.
- [x] 3.6 Verify `read_project_context` returns the authoritative project `visualStyle` and define explicit behavior when it is absent.

## 4. Structured save contract and validation

- [x] 4.1 Add failing JSON Schema tests for `save_episode_storyboards`, including schema version, episode fingerprint, ordered storyboard numbers, source markers, used asset keys, ordered internal shots, decimal durations, positioning, action, and verbatim sound fields.
- [x] 4.2 Register `save_episode_storyboards` as a WRITE tool and add it to the project/episode/write allowlists with strict tenant, project, script, episode, execution, attempt, and Agent Run scope enforcement.
- [x] 4.3 Implement source-boundary resolution that requires ordered, non-overlapping markers to cover all meaningful current-episode content and rejects missing, duplicate, ambiguous, or reordered spans.
- [x] 4.4 Implement verbatim dialogue, narration, and inner-OS validation that requires every source utterance to occur exactly once and rejects translation, rewriting, invention, omission, or duplication.
- [x] 4.5 Implement structural duration validation: storyboard totals 10–15 seconds, internal shots 1.5–4 seconds, storyboard and shot numbering starts at 1 without gaps, and compatibility duration uses standard rounding such as 12.8 to 13.
- [x] 4.6 Implement deterministic material resolution using valid supplied keys, current-episode visual bindings, project-primary variants, exact canonical names, and exact aliases; reject ambiguous keys and preserve unmatched names as ordinary text with `ASSET_PENDING`.

## 5. Prompt rendering and atomic persistence

- [x] 5.1 Add renderer fixture tests for the confirmed complete prompt format, exact fixed no-subtitle/BGM sentence, project visual style, actual-use-only material categories, scene setting, time, lighting, internal shots, and fixed consistency constraint.
- [x] 5.2 Implement a deterministic storyboard prompt renderer that creates text and material Mention nodes for every matched occurrence in references, scene setting, positioning, and action while avoiding duplicate appearance prose for bound assets.
- [x] 5.3 Serialize the prompt document as the authoritative rich document and derive `video_prompt` as its compatible plain-text form without losing the exact user-confirmed wording.
- [x] 5.4 Implement one-transaction episode replacement that locks and rechecks the episode fingerprint, validates the full payload before mutation, retires every old active storyboard regardless of status, and inserts only records generated by the current Run.
- [x] 5.5 Add rollback and regeneration tests proving invalid/stale payloads preserve all old rows, valid reruns replace the complete set, old generated-media rows remain stored, and new storyboard IDs inherit no historical media binding.

## 6. Workflow Agent run contract and execution integration

- [x] 6.1 Add `short-drama-storyboard` to `WorkflowAgentRunContract` with the exact read sequence and terminal `save_episode_storyboards`, and test missing, duplicate, skipped, and out-of-order calls.
- [x] 6.2 Add a storyboard Agent Adapter that creates one episode-scoped formal Run and reports success only when the current active storyboard set is non-empty, complete, and entirely committed by that Run.
- [x] 6.3 Route `storyboard_breakdown` asynchronous executions through the storyboard Agent Adapter using the project's frozen TEXT model while preserving existing execution, attempt, call-log, usage, cost, point-settlement, cancellation, and claim-loss behavior.
- [x] 6.4 Configure at most three retryable execution attempts and verify terminal schema, stale-source, provider, and missing-save failures preserve prior formal storyboards and expose actionable diagnostics.
- [x] 6.5 Remove the mansion, banquet-hall, and equity-agreement fixed inserts from both asynchronous and obsolete synchronous storyboard breakdown paths, with a regression test using unrelated episode content.

## 7. Episode-scoped API and storyboard page

- [x] 7.1 Change the storyboard breakdown request contract to require one active `episodeId`, reject full-script and selected-text scopes, and return the unified asynchronous execution response with idempotency preserved.
- [x] 7.2 Add backend controller/service tests for authorization, episode ownership, duplicate idempotency keys, execution status, successful refresh, and failure preserving prior data.
- [x] 7.3 Add a “生成本集分镜” action to the selected episode area, disable it while its execution is active, show the shared AI execution status, and refresh only after formal Run completion.
- [x] 7.4 Replace the plain prompt textarea with one accessible editing surface supporting text and material Mention nodes while retaining the existing single-input layout and saved complete prompt semantics.
- [x] 7.5 Add frontend tests for selected-episode submission, no automatic generation, one visible prompt editor per storyboard, multiple internally numbered shots, material-tag display, unmatched plain text, and user deletion not auto-rebinding a tag.

## 8. Verification and rollout

- [x] 8.1 Run focused backend tests for Workflow Agent contracts, screenplay tools, storyboard validation/rendering/persistence, execution integration, controller behavior, and Flyway schema migration.
- [x] 8.2 Run the complete backend test suite and resolve regressions without weakening the new formal save contract.
- [x] 8.3 Run frontend storyboard tests, TypeScript checking, Biome lint, Ant Design lint, and production build.
- [x] 8.4 Exercise one successful generation and one failed regeneration against the local application, verifying prompt formatting, material Mention metadata, atomic replacement, prior-data preservation, Agent Run audit, AI usage, and point settlement.
- [x] 8.5 Enable the storyboard Workflow Agent feature flag for the intended environment only after verification and document rollback by disabling the flag without dropping new schema or audit data.

## 9. Source-segment planning stabilization

- [x] 9.1 Add deterministic episode source segmentation with episode-local `S0001` IDs, exact text and offsets, type classification, metadata exclusion, and fingerprint-bound invalidation tests.
- [x] 9.2 Replace source text markers and copied spoken fields in the save schema with contiguous source ranges and spoken-segment references; add coverage, stale-source, unknown-ID, gap, overlap, order, and exact spoken-text tests.
- [x] 9.3 Make the storyboard execution host perform and audit the five trusted reads before one combined planning request while preserving scope guards and excluding old storyboards.
- [x] 9.4 Return structured save diagnostics and prevent deterministic validation failures from restarting the complete workflow; retain bounded retries for provider transport failures.
- [x] 9.5 Update storyboard Skills and fixtures for source-segment ranges, then run focused and complete backend verification plus frontend regression checks.
- [x] 9.6 Deploy behind the existing feature flag and complete a successful online generation with Run audit, prompt/Mention, atomic replacement, usage, and point-settlement evidence.

## 10. Storyboard planning context pruning

- [x] 10.1 Add bounded Unicode-safe opening and ending excerpts to adjacent episode context without exposing complete adjacent bodies.
- [x] 10.2 Add a storyboard-only reducer that removes full-project script content, raw analysis responses, execution metadata, and verbose unused asset data while preserving the complete current episode and source segments.
- [x] 10.3 Integrate reduction after all five trusted reads are audited, keep the original tool-step evidence unchanged, and log only input/output sizes and retained-item counts.
- [x] 10.4 Run focused and complete backend verification and strict OpenSpec validation (756 tests, 0 failures, 0 errors, 1 intentional skip).
- [ ] 10.5 Deploy behind the existing feature flag and record project 26 input size, model duration, total duration, storyboard coverage, material bindings, call count, and point settlement against execution 91399.
