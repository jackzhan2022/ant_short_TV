## 1. Baseline and additive schema

- [x] 1.1 Verify the existing global-understanding Agent change, migrations, backend tests, and frontend tests as the implementation baseline without modifying unrelated working-tree changes.
- [x] 1.2 Add failing schema migration tests for `script_episode_summary`, current-script asset metadata, visual-variant metadata, provenance fields, indexes, and legacy-compatible nullability.
- [x] 1.3 Add the additive Flyway migration for the formal episode-summary table and script-scoped/extensible asset and variant fields without dropping legacy columns.
- [x] 1.4 Add or extend entities, mappers, repositories, and response models for formal summaries, asset JSON metadata, generating Run references, and current formal episode reads.
- [x] 1.5 Add transactional backfill tests and implement conservative backfill of non-empty legacy episode summaries and only unambiguous asset script ownership.

## 2. Shared trusted episode tools and Agent contracts

- [x] 2.1 Add failing tool-schema and authorization tests for `read_current_episode`, including tenant/project/script/episode isolation, active status, bounded output, and empty model-supplied business identity.
- [x] 2.2 Implement `read_current_episode` to return the trusted current episode, content fingerprint, opaque episode key, and a compact current-script asset/variant catalog while recording server-side Run state.
- [x] 2.3 Add failing tests that generalize required tool sequences and terminal tools for the split, summary, and recognition Agent codes, including rejection of missing, repeated-out-of-order, and unapproved tool calls.
- [x] 2.4 Generalize `WorkflowAgentRunContract`, bounded save-payload checks, and Run-state tracking for the three new read/save sequences.
- [x] 2.5 Add failing scope-guard tests for stage-specific Agent identity, trusted execution attempts, script-scoped split Runs, and episode-scoped summary/recognition Runs.
- [x] 2.6 Generalize `WorkflowAgentScopeGuard` tool sets, write permissions, and analysis-stage validation without weakening the existing global-understanding checks.
- [x] 2.7 Add typed stale-script, stale-episode, stale-snapshot, ambiguous-entity, and incomplete-Agent error contracts and safe API mappings.

## 3. Skills and Agent definitions

- [x] 3.1 Add baseline behavioral fixtures for episode boundary selection, summary fidelity, stable entity naming, alias reuse, same-name ambiguity, character looks, and prop-state boundaries before authoring the new Skills.
- [x] 3.2 Create and validate `short-drama-episode-splitting-framework/SKILL.md` with exact-source boundary, complete-coverage, title, no-rewrite, and pre-save checks.
- [x] 3.3 Create and validate `short-drama-episode-summary-framework/SKILL.md` with chronological summary, two-to-five highlights, nullable evidenced ending hook, per-episode scope, and pre-save checks.
- [x] 3.4 Create and validate `short-drama-asset-recognition-framework/SKILL.md` with stable keys, semantic identity, aliases, character-look, scene, prop-state, evidence, and no-fuzzy-merge rules.
- [x] 3.5 Add Skill contract tests proving each new Skill loads from `backend/skills`, has valid frontmatter, preserves its responsibility boundary, and works with the shared foundation Skill.
- [x] 3.6 Add bootstrap tests for the three stable Agent codes, ordered Skill bindings, exact tool allowlists, operational prompts, compatible tool-calling model selection, model parameters, and idempotent startup.
- [x] 3.7 Implement the three Agent bootstraps and feature flags so unavailable models or disabled migration flags leave legacy paths operable without creating partial definitions.

## 4. Episode-splitting Agent and formal episode reconciliation

- [x] 4.1 Add failing JSON Schema tests for `save_episode_splitting`, including ordered titles/markers, limits, additional-property rejection, and absence of model-supplied business IDs or episode bodies.
- [x] 4.2 Add failing boundary resolver tests for valid complete coverage, repeated markers, missing markers, reversed markers, overlap, gaps, preamble preservation, whitespace tails, and exact formatting preservation.
- [x] 4.3 Implement the split-save boundary resolver against the server-recorded script snapshot and reject invalid responses without a successful one-episode fallback.
- [x] 4.4 Add reconciliation tests for stable-ID retention, insertion, reordering, conservative ambiguity, retirement, transaction rollback, and idempotent replay of one Run.
- [x] 4.5 Extend formal episode reconciliation to record generating Run provenance and to retire downstream variant bindings as inactive/retired instead of `REVIEW_REQUIRED`.
- [x] 4.6 Implement and register `save_episode_splitting` with trusted source-hash checks, exact extraction, atomic reconciliation, formal result reference, and compatibility result snapshot.
- [x] 4.7 Add split-stage adapter tests proving every Run invokes AI even when headings exist and succeeds only after the terminal save commits.
- [x] 4.8 Implement the `short-drama-episode-splitting` adapter, replace the legacy parser bypass for the migrated path, and retain a feature-flagged rollback to the legacy executor.
- [x] 4.9 Add authorized endpoints/service commands for independent split regeneration and failed split-stage retry against the current script.

## 5. Per-episode fan-out coordination

- [x] 5.1 Add coordinator tests for frozen active-episode snapshots, child Run creation, configurable concurrency, shared frozen Agent/model revision, unit counts, and no duplicate scheduling.
- [x] 5.2 Implement the fan-out coordinator for one child workflow Agent Run per snapshot episode using existing task, stage, execution, attempt, and Run identities.
- [x] 5.3 Add progress aggregation tests for pending, running, succeeded, partially failed, failed, cancelled, and restored child Runs, with monotonic parent percentages.
- [x] 5.4 Implement persisted fan-out progress aggregation and expose total, completed, failed, current episode, retryability, and child Run references in analysis responses.
- [x] 5.5 Add retry tests proving only failed, missing, or stale units rerun by default, full regeneration is explicit, and changed episode sets create a new snapshot.
- [x] 5.6 Implement targeted retry, stale-snapshot rejection, cancellation propagation, and finalizer invocation only after complete current snapshot coverage.

## 6. Episode-summary Agent and formal editable summaries

- [x] 6.1 Add failing JSON Schema tests for non-blank `summary`, two-to-five string `highlights`, nullable `endingHook`, payload limits, and forbidden identity fields.
- [x] 6.2 Add repository and service tests for one formal summary per episode, first insert, complete overwrite, stale fingerprint rejection, transaction rollback, source/provenance, and legacy summary mirroring.
- [x] 6.3 Implement `ScriptEpisodeSummary` persistence and `save_episode_summary` using only trusted Run scope and the server-recorded current episode fingerprint.
- [x] 6.4 Add summary Agent tests proving one episode per Run, ordered Skill loading, read-before-save, no global-understanding dependency, no invented ending hook, and required terminal save.
- [x] 6.5 Implement and register the summary tool and `short-drama-episode-summary` child adapter, then route `EPISODE_SUMMARY` through the fan-out coordinator behind its feature flag.
- [x] 6.6 Add current formal summary query and edit endpoints for summary, highlights, and ending hook, including per-episode regeneration and explicit overwrite authorization.
- [x] 6.7 Add compatibility tests proving new consumers prefer `script_episode_summary.content_json` while legacy consumers continue receiving mirrored `script_episode.summary`.

## 7. Asset-recognition Agent, deterministic matching, and finalization

- [x] 7.1 Add failing JSON Schema tests for characters, character looks, scenes, props, and prop states with run-local keys, optional trusted keys, owner references, evidence anchors, limits, and forbidden derived-prop fields.
- [x] 7.2 Add semantic normalization tests for stable Chinese names, whitespace/punctuation normalization, explicit aliases, same-name identity conflicts, physical scene identity, generic owned props, character-look ownership, and prop-state ownership.
- [x] 7.3 Add deterministic matcher tests for trusted-key reuse, exact canonical names, exact aliases, no match creation, multi-match rejection with safe candidates, cross-tenant/project/script/type isolation, and concurrent duplicate prevention.
- [x] 7.4 Implement the compact script asset catalog and opaque key resolver without exposing raw untrusted database identity or excessive metadata to the model.
- [x] 7.5 Implement the formal asset matcher with row locks, active uniqueness, explicit alias evidence, typed `ENTITY_MATCH_AMBIGUOUS`, and no fuzzy automatic merge.
- [x] 7.6 Add transactional persistence tests proving one invalid item rolls back the complete episode payload and idempotent replay replaces only that episode's Agent-managed bindings.
- [x] 7.7 Implement `save_episode_assets` to validate source evidence, upsert formal script-scoped identities and variants, preserve stable media references, and replace episode bindings transactionally.
- [x] 7.8 Add tests proving character looks and prop states reuse `asset_visual_variant`, multiple active forms can bind to one episode, only one preferred form is enforced, and scene time/atmosphere remains usage metadata.
- [x] 7.9 Add finalizer tests for complete coverage, partial failure with no retirement, inactive binding retirement, zero-binding AI asset retirement, and preservation of matched IDs, media, legacy rows, and user-created assets.
- [x] 7.10 Implement recognition finalization after full current snapshot success and retain bounded raw/normalized diagnostics without requiring candidate confirmation.
- [x] 7.11 Add recognition Agent tests proving read-before-save, stable-key reuse, tool-error self-correction, no summary/global dependency, five-category output, and required terminal save.
- [x] 7.12 Implement and register the recognition tool and `short-drama-asset-recognition` child adapter, then route `CHARACTER_SCENE_RECOGNITION` through fan-out behind its feature flag.
- [x] 7.13 Preserve legacy extraction, normalization candidate, merge, and confirmation APIs for old callers while preventing them from blocking or replacing current formal Agent results.

## 8. Pipeline completion, APIs, and workbench UI

- [x] 8.1 Add pipeline coordinator tests proving all four stages invoke workflow Agents in order, each reads its own trusted source, and later stages cannot be marked successful after an earlier failure.
- [x] 8.2 Replace legacy stage success checks with terminal-save and current formal-coverage checks while retaining bounded diagnostic `script_analysis_result` references.
- [x] 8.3 Add current-analysis response fields for formal result references, per-episode units, partial failures, current Agent action, stale state, and independent rerun availability.
- [x] 8.4 Update production-workbench service types and requests to load formal episodes, formal summaries, script-scoped assets, variants, episode bindings, and Agent progress without editing generated service code.
- [x] 8.5 Update the script page to render four fixed stages, completed/total episode units, current and failed episodes, independent retry/regeneration actions, and explicit overwrite warnings.
- [x] 8.6 Replace completed-result rendering from legacy stage JSON with formal global-understanding, episode, summary, character, character-look, scene, prop, and prop-state data.
- [x] 8.7 Add editable UI flows for episode summaries and existing asset/variant fields while preserving stable IDs and current image-generation controls.
- [x] 8.8 Add frontend tests for initial loading, active split, summary fan-out, recognition partial failure, reload restoration, single-unit retry, formal completed rendering, and no candidate-confirmation gate.

## 9. Verification, rollout, and rollback

- [x] 9.1 Run focused migration, repository, tool schema, scope, Agent runner, split, summary, matcher, finalizer, coordinator, controller, and frontend test suites and resolve all failures.
- [x] 9.2 Run the full backend test suite plus frontend type-check, Jest tests, Biome lint, and Ant Design lint required by the repository.
- [x] 9.3 Add end-to-end coverage for a multi-episode script proving AI-only split, stable episode IDs, per-episode summaries, cross-episode character reuse, multiple looks, prop-state binding, progress restoration, and formal page reads.
- [x] 9.4 Add failure end-to-end coverage for stale source, invalid split coverage, ambiguous asset match, one failed child Run, targeted retry, transaction rollback, and no premature retirement.
- [x] 9.5 Verify model compatibility and realistic token, timeout, concurrency, call-count, billing, and payload limits with the configured DeepSeek-compatible tool-calling path.
- [x] 9.6 Document new environment flags, concurrency/retry defaults, formal table ownership, legacy compatibility reads, rollout order, monitored error codes, and rollback procedure.
- [x] 9.7 Enable and smoke-test the three adapters sequentially—split, summary, recognition—in a non-production environment before production rollout.

## 10. Full-script-first splitting and automatic chunk fallback

- [x] 10.1 Add failing compatible-provider request tests for an optional thinking-mode control and implement DeepSeek `thinking.type=disabled` emission without sending the vendor field to unrelated models.
- [x] 10.2 Add failing split prompt and bootstrap tests that prohibit visible analysis/source repetition, retain boundary-only output, and replace the temporary 32K brute-force configuration with measured full-path and fallback budgets.
- [x] 10.3 Add failing migration tests and an additive migration for `script_split_snapshot` and `script_split_chunk`, including tenant/script/hash scope, parent Run, offsets, statuses, bounded candidates, call references, progress, and stale/retry indexes.
- [x] 10.4 Add repository tests and implement persisted split snapshots, chunk units, mode/fallback reason, monotonic progress, successful-unit reuse, cancellation, and stale-source invalidation.
- [x] 10.5 Add failing structure-index tests for explicit headings, heading-free scenes, paragraph and line signals, long single paragraphs, Unicode-safe hard cuts, 15K–20K targets, 24K caps, and roughly 1.5K overlap.
- [x] 10.6 Implement the deterministic `ScriptSplitChunkPlanner` and trusted `read_script_structure` tool without promoting structural or fixed-length cuts to formal episode boundaries.
- [x] 10.7 Add failing chunk-analysis tests for bounded concurrency, audited model calls, verified local-to-absolute markers, overlap deduplication, repeated source text, compact trusted-anchor output, partial failure, and failed-only retry.
- [x] 10.8 Implement `ScriptSplitChunkAnalyzer` and `analyze_script_chunks` as one logical tool backed by internal model calls and persisted chunk results.
- [x] 10.9 Add failing dynamic-contract and runner tests for normal and fallback tool sequences, context preflight, context errors, `finish_reason=length`, empty/no-save responses, clean fallback context, single fallback transition, validation-error exclusion, and one terminal save.
- [x] 10.10 Implement the split-specific execution strategy while keeping the generic workflow runner unchanged for other Agents; record mode, fallback reason, model calls, token usage, and phase-specific idempotency.
- [x] 10.11 Add API and workbench tests for restored split mode, fallback reason, chunk totals/completion/failures, current action, and one visible splitting stage, then implement the response and UI fields.
- [x] 10.12 Run focused backend and frontend tests, then full repository verification, and document the new context threshold, chunk sizing, concurrency, retry, thinking-mode, rollout, monitoring, and rollback settings.
- [x] 10.13 Run project 26 against the latest DeepSeek-compatible model, first verify the non-thinking full path, then force and verify chunk fallback, exact full-text coverage, one formal save, stable episode IDs, restored progress, billing, and downstream summary/recognition startup.
