## Context

The four-stage script analysis pipeline currently mixes two execution models. Global understanding uses the workflow-Agent runtime, loads file-backed Skills, reads the current script through a trusted tool, and succeeds only after a formal write tool commits. Episode splitting, episode summaries, and asset recognition still use the legacy stage executor and stage-result JSON. The split path may bypass AI when headings are detected and may silently collapse an invalid response to one episode; summaries are normally generated in one batch and only `script_episode.summary` is formal; asset recognition produces project-scoped review candidates and does not recognize character looks or prop states.

The repository already provides the main reusable primitives: `script_episode` stable identities and reconciliation, workflow Agent definitions and run/step snapshots, AI execution and billing identity, `asset_visual_variant`, `asset_visual_variant_episode`, asset normalization, and the four-stage progress UI. The design must build on those primitives, preserve existing production assets and images, remain tenant/project/script isolated, and tolerate scripts with more episodes than one workflow-Agent conversation can process under the current step and timeout limits.

The product decisions are fixed: all three Agents can run independently against the current script state; the second Agent always uses AI and writes episodes with overwrite reconciliation strategy A; the third and fourth Agents run per episode; formal results are immediately editable; no script-version history is required for Agent ownership; retired episode bindings become inactive without a review state; the fourth Agent recognizes characters, character looks, scenes, props, and states of the same prop, but not derived-prop relationships.

## Goals / Non-Goals

**Goals:**

- Register three enabled, independently runnable workflow Agents with two ordered Skills and two allowed business tools each.
- Make current script or current active episode content the only authoritative model input and reject stale saves by server-recorded hashes.
- Persist episode content, episode summaries, assets, visual variants, and episode bindings as formal editable data before a stage can succeed.
- Preserve stable episode and asset identities through deterministic reconciliation while preventing fuzzy or ambiguous automatic merges.
- Fan out long-running summary and asset stages into bounded per-episode Agent Runs with restorable progress and isolated retries.
- Reuse existing canonical asset, visual-variant, binding, execution, billing, and progress infrastructure where it satisfies the new semantics.
- Migrate the workbench from legacy stage JSON and candidate confirmation to formal current-state reads without destroying compatibility data.

**Non-Goals:**

- Adding script-version rollback or Agent-output history as a user-facing feature.
- Generating images, storyboards, video, or missing visual variants as part of analysis.
- Recognizing shot-level asset occurrence or ordering multiple occurrences within one episode.
- Creating relationships between distinct props, including copy, container, component, or derived-from relationships.
- Automatically merging fuzzy, cross-language, or ambiguous asset matches.
- Removing legacy analysis-result, episode-summary, asset, or image columns in this change.

## Decisions

### Use three workflow Agents and keep prompts operational

Seed `short-drama-episode-splitting`, `short-drama-episode-summary`, and `short-drama-asset-recognition` idempotently when a compatible enabled text tool-calling model exists. Each Agent loads `short-drama-analysis-foundation` first and its dedicated framework Skill second. System prompts only state the required read/analyze/save sequence and completion condition; domain semantics live in the dedicated Skill, while exact payload constraints remain authoritative JSON Schema in the save tool.

The split Agent allows `read_current_script` and `save_episode_splitting`. The summary and asset Agents allow a shared `read_current_episode` plus their own `save_episode_summary` or `save_episode_assets`. Reusing the broad legacy asset-recognition prompts was rejected because they cannot express trusted scope, required tool completion, per-episode identity, looks, prop states, or formal persistence.

### Run splitting once, but fan out summary and recognition by episode

Episode splitting is one script-scoped Agent Run because it must reason about boundaries across the complete current script. Summary and recognition are script-level stages coordinated as bounded sets of episode-scoped Agent Runs. Each child run receives a trusted `episodeId`, calls the read tool once, and calls its save tool once. The coordinator limits concurrency, records the shared stage attempt, updates `total_units` and `completed_units`, and starts finalization only when every current snapshot episode succeeds.

One conversation with N save calls was rejected: the workflow runtime defaults to twenty steps and a five-minute timeout, large scripts would exceed both the step budget and model context, and a single malformed episode would make retry and progress unnecessarily coarse. Creating different Agent definitions per episode was also rejected; one definition with many scoped runs preserves configuration and audit consistency.

### Prefer full-script boundary submission and automatically fall back to chunks

The normal split path remains `read_current_script -> save_episode_splitting`. The model receives the complete trusted script but returns only titles and exact source markers; it never returns episode bodies. For DeepSeek V4 Chat Completions, the request explicitly sets `thinking.type=disabled`, because the provider otherwise enables high-effort thinking by default and can exhaust the output budget before the terminal tool call. The operational prompt also forbids visible analysis, source repetition, and per-episode explanation before the save call.

Before contacting the model, the split runner estimates the complete request budget from the composed prompt, tool schemas, current script, and reserved tool output. If that request exceeds a configured safe context threshold, it skips the predictably failing full-text call. If a full-text call returns a context-length error, `finish_reason=length`, an empty response, or no required save call, the same user-visible Run switches once to an isolated fallback phase. Failed generated text is retained only in bounded audit logs and is never appended to the fallback model context. Marker validation, stale-source, overlap, gap, and other business errors do not trigger fallback.

The fallback path is `read_script_structure -> analyze_script_chunks -> save_episode_splitting`. Structural indexing prefers episode headings, scene headings, time/location changes, paragraph boundaries, and ordinary line breaks, using a safe character boundary only when necessary. Chunks target 15,000–20,000 characters, cap at 24,000, and overlap by about 1,500 characters. These boundaries constrain model input only; they are not formal episode boundaries.

`analyze_script_chunks` is one logical trusted tool call that schedules bounded internal AI calls with configurable concurrency. Each chunk returns verified candidate markers, absolute offsets, evidence, confidence, and source chunk. The service deduplicates overlap candidates by absolute position and returns a compact candidate and trusted-anchor catalog to a clean fallback aggregation call. Only the aggregation call can invoke `save_episode_splitting`, and the existing save tool remains the sole formal writer.

Persist `script_split_snapshot` for the trusted source hash, fallback status, chunk counts, configuration version, parent Run, and timestamps, plus `script_split_chunk` for core/context offsets, status, call reference, bounded candidate JSON, and typed failure. Original script text is not duplicated. Successful hash-matching chunks can be reused on retry; a changed source marks the snapshot stale. The page continues to show one split Agent and may expand its current mode, fallback reason, and chunk progress.

### Treat source snapshots and hashes as optimistic concurrency tokens

`read_current_script` records the current `script.content` SHA-256 in server-side run state. `read_current_episode` records the trusted episode ID, content fingerprint, status, and the stage attempt's episode-set snapshot. Models receive opaque episode and asset keys but never establish trusted business identity by echoing database IDs.

Every save tool verifies the recorded read, current ownership, active status, stage/attempt identity, and unchanged hash before entering its persistence transaction. A mismatch returns a typed stale-source error and writes nothing. This permits independent reruns after ordinary script edits without binding the Agent output to historical `script_version_id`.

### Always use AI boundaries and extract exact source text on the server

The split Agent returns ordered episode titles and source boundary markers, not rewritten episode bodies. `save_episode_splitting` resolves every marker against the exact script snapshot, requires monotonic non-overlapping boundaries and full non-whitespace coverage, and extracts the formal `content` substrings itself. Duplicate, missing, reversed, or uncovered markers fail the stage; there is no successful one-episode fallback for an invalid AI response. Explicit headings remain useful evidence in the Skill but never bypass the AI invocation.

After validation, the existing conservative reconciler matches current active episodes by strong heading/content evidence. Matched episodes retain IDs, new episodes receive IDs, and disappeared episodes are retired. Retired episode visual bindings are retained for audit but set inactive/retired rather than `REVIEW_REQUIRED`. Reconciliation and all dependent status changes commit atomically.

### Store summary as a one-to-one extensible formal document

Add `script_episode_summary`, uniquely keyed by tenant and `episode_id`, with `script_id`, `schema_version`, `content_json`, `source`, `generated_by_run_id`, and timestamps. The initial schema requires `summary`, `highlights`, and nullable `endingHook`. A per-episode save replaces the complete current document for that episode. `script_episode.summary` is mirrored during migration for old consumers but is no longer authoritative.

A column per summary field was rejected because each future summary dimension would require another migration. Storing only stage-result JSON was rejected because it is not a stable editable domain resource and cannot support one-episode edits or retries.

### Reuse canonical asset and visual-variant tables with script-scoped metadata

Keep `character_asset`, `scene_asset`, and `prop_asset` as canonical identities to avoid breaking image and storyboard consumers, but add current-script ownership and extensibility needed by the new path: nullable/backfillable `script_id`, `normalized_name`, `content_json`, `source`, and `generated_by_run_id`, plus script-scoped active lookup indexes. New Agent-created assets always have non-null `script_id`; legacy rows remain readable and are backfilled only when ownership is unambiguous.

Reuse `asset_visual_variant` for both character looks and prop states. Add structured `content_json` and `generated_by_run_id`; a variant carries `variantKind=CHARACTER_LOOK` or `variantKind=PROP_STATE`. Reuse `asset_visual_variant_episode` for episode occurrence and form usage. Primary variants provide the base occurrence path for characters, scenes, and props; multiple active variants may be bound to one episode while at most one remains preferred under the existing constraint. Separate character-look and prop-state tables were rejected because their identity, media lifecycle, and episode-binding behavior duplicate the existing variant abstraction.

### Make matching deterministic and tool-authoritative

`read_current_episode` returns a compact catalog of existing script assets and variants using opaque keys, stable names, explicit aliases, and only the disambiguating metadata needed by the Skill. The Agent must reuse a supplied key when it can establish identity and use run-local keys only for new entities.

`save_episode_assets` resolves in this order: validated supplied key; exact normalized canonical name; exact explicit alias; otherwise new asset. Fuzzy similarity never auto-merges. A name or alias that resolves to multiple records, or conflicts with strong identity evidence, produces `ENTITY_MATCH_AMBIGUOUS` with safe candidate keys so the Agent can correct and retry. Character looks match only inside their owner character; prop states match only inside their owner prop. Database active-name indexes, row locks, idempotency keys, and per-episode replacement prevent concurrent duplicates.

The recognition Skill defines semantic identity: roles are not split by nickname or title; scene time/atmosphere does not create a new physical location; generic same-type props owned by different characters are distinct; costume changes require visible production differences; prop states require visible state changes and never create relationships to a different prop. Each output item includes a short source evidence anchor that the save tool verifies against the episode snapshot.

### Persist each episode transactionally and retire only after full recognition success

Each `save_episode_assets` call upserts formal identities and variants, then replaces Agent-managed bindings for that episode in one transaction. It never retires whole-script assets. Once every episode in the frozen snapshot succeeds, a server-side finalizer retires AI-managed assets and variants that have no active binding in the successfully replaced snapshot, while preserving stable IDs for matches, image/generation state, legacy references, and user-created resources. A partial failure leaves completed episode data visible but does not execute global retirement; retries target only failed or stale episode units.

The existing normalization parser, name normalizer, aliases, raw-response diagnostics, and validation errors are reused where possible, but the new Agent path auto-promotes deterministic valid results directly. The legacy candidate-review APIs remain available for legacy extraction calls during migration; the new stage does not wait for candidate confirmation.

### Define explicit overwrite and editing semantics

Agent output is formal current-state data. An explicit regenerate action authorizes replacement of the generated episode set, summary document, or Agent-managed asset metadata/bindings in that action's scope. The UI warns that regenerated content replaces current generated/editable content. Stable IDs, uploaded/generated media, and unrelated user-created assets are preserved. Manual edits use the same formal tables and update `source=USER`; a deterministic match may reuse such an identity and bind it, but the recognition Agent does not overwrite user-owned descriptive fields unless the regenerate request explicitly includes replacement of that asset.

### Derive stage success and page state from committed domain data

Adapters parallel to the global-understanding adapter invoke each Agent or fan-out coordinator from the legacy four-stage task. A stage reaches `SUCCEEDED` only after its terminal save contract is satisfied and its formal coverage check passes. Summary and recognition expose `totalUnits`, `completedUnits`, current episode, failed episode keys, retryability, and child Run references. The workbench restores this state from server-side runs and reads completed content from formal repositories, not legacy normalized JSON. Legacy result rows continue to store bounded diagnostic snapshots and result references for compatibility and audit.

## Risks / Trade-offs

- [Per-episode model calls cost more than one batch call] → Bound concurrency, freeze one model configuration per stage attempt, reuse compact catalogs, and expose accurate billing per child Run.
- [Compact asset catalogs may still grow for very large scripts] → Return opaque keys plus minimal matching metadata, paginate or rank catalogs if measured limits are exceeded, and never omit exact-name/alias candidates.
- [AI boundary markers may be repeated or unstable] → Require enough marker context, resolve from the prior cursor, validate full coverage, and fail rather than guess.
- [DeepSeek defaults to expensive thinking and may never call the save tool] → Disable thinking for the full-text split request, prohibit explanatory output, retain finish diagnostics, and fall back only on capacity/incomplete-call signals.
- [Chunk fallback increases calls and implementation complexity] → Keep full-text analysis as the normal path, enter fallback at most once per Run, cap concurrency, persist chunk results, and reuse only unchanged successful chunks.
- [Direct formal asset writes can expose partial fourth-stage results] → Mark the stage partial, retain per-episode provenance, withhold global retirement until full success, and allow isolated retry.
- [Exact matching can create duplicates when aliases are unknown] → Require stable naming in the Skill, supply the existing catalog every run, persist aliases, reject ambiguity, and provide later manual merge without fuzzy automatic mutation.
- [Adding `script_id` to project-scoped assets affects old consumers] → Add fields and indexes without removing old columns, keep legacy rows readable, and scope only new Agent writes until backfill is proven.
- [Explicit regeneration may overwrite user edits] → Warn before regeneration, scope single-episode retries narrowly, preserve user-created identities/media, and record the generating Run.
- [The three-Agent migration touches uncommitted first-Agent work] → Implement against a verified first-Agent baseline and keep migrations additive and sequential.

## Migration Plan

1. Finish and verify the global-understanding Agent baseline, then add additive schema migrations for formal summaries and asset/variant script-scoped metadata.
2. Add shared current-episode read state, generalized Agent contracts/scope validation, fan-out coordination, feature flags, and progress response fields behind disabled flags.
3. Add and validate each dedicated Skill, tool schema/executor, Agent bootstrap, and stage adapter in dependency order: splitting, summary, recognition.
4. Backfill `script_episode_summary` from non-empty legacy summaries and backfill asset `script_id` only for unambiguous single-script ownership; retain legacy data otherwise.
5. Enable the split Agent first and verify stable reconciliation, then summary fan-out, then recognition fan-out and finalization.
6. Switch workbench reads and edit endpoints to formal data while dual-writing compatibility fields and retaining legacy analysis-result diagnostics.
7. Monitor stale-save failures, split coverage failures, ambiguous matches, per-episode latency/cost, orphaned bindings, and legacy fallback reads before removing any old path in a later change.

Rollback disables the three Agent adapters and restores legacy executors/read paths. Additive tables and columns remain; no migration deletes user data. Formal rows created while enabled remain available for a later re-enable and are not destructively converted back into candidates.

## Open Questions

No product-blocking questions remain for implementation. Concurrency limits, catalog size limits, retry counts, and initial model token limits are operational defaults to choose from measured tests and expose through existing configuration rather than hard-code in the domain contract.
