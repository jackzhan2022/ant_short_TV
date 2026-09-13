## Context

The production workbench currently uses `character_asset`, `scene_asset`, and `prop_asset` as both logical identity and visual-generation targets. AI extraction results are converted directly into draft rows, exact names prepare `merge_target_id`, and existing image result columns act as the effective visual representation. Parsed episodes are response values rather than stable persisted resources, so no durable relation can express that a costume or alternate scene appearance applies to specific episodes.

Recent production failures also demonstrated that a transport-successful model response can contain compatible but unexpected shapes, such as string arrays, and reach database insertion with a missing required name. The design must keep raw evidence, reject or normalize malformed shapes before persistence, preserve confirmed assets, and remain compatible with current workspace and downstream media flows while the richer model is introduced.

## Goals / Non-Goals

**Goals:**
- Preserve character, scene, and prop identity independently from generated or uploaded visual representations.
- Support multiple visual variants per logical asset with a primary fallback and explicit generation lifecycle.
- Persist stable episode identities and preferred visual-variant bindings by episode.
- Normalize, validate, deduplicate, and review AI-recognized candidates before promoting them into canonical assets.
- Preserve raw/normalized evidence and deterministic merge decisions for retry, audit, and diagnosis.
- Migrate existing assets and media references without silently merging or deleting user-confirmed data.

**Non-Goals:**
- Redesigning image-provider transport, billing, or the unified AI execution lifecycle.
- Automatically generating every missing visual variant during recognition.
- Inferring shot-level continuity or replacing the storyboard editor in this change.
- Automatically accepting ambiguous cross-language aliases or destructive merges without user review.

## Decisions

### Keep existing asset tables as canonical identities

`character_asset`, `scene_asset`, and `prop_asset` remain the canonical logical-asset tables. A new `asset_visual_variant` table references an asset by tenant, project, asset type, and asset id and stores variant name, appearance/prompt metadata, source, generation state, current image result, primary marker, and soft-delete timestamps.

This avoids a breaking migration to a shared `script_asset` parent while still giving all asset types one consistent variant lifecycle. A polymorphic reference cannot use one database foreign key, so service-layer validation and tenant/project/type composite indexes are mandatory. Separate variant tables were considered but rejected because their lifecycle, API shape, and image-generation behavior would be duplicated.

### Persist stable episodes and reconcile them across script versions

Add a project-scoped `script_episode` identity containing script id, stable key, current version reference, episode number, title, summary, content fingerprint, and lifecycle fields. Parsing or intelligent splitting reconciles the new episode set against the current set using explicit heading identity first and normalized content anchors second. Unchanged episodes retain ids; added episodes receive ids; removed episodes are retired rather than hard-deleted.

Binding directly to episode numbers was rejected because insertions and reordering would silently point variants at the wrong content. Binding to immutable version-only episode rows was also rejected as the sole model because every ordinary save would orphan current bindings. Reconciliation preserves identity where evidence is strong and requires review when it is ambiguous.

### Model episode selection as a preferred binding

Add `asset_visual_variant_episode` linking a variant to a stable episode. Multiple variants may retain historical or alternative bindings, but only one active preferred variant per logical asset and episode is allowed. Downstream resolution uses the preferred episode binding first, then the logical asset's primary variant, then the existing legacy image reference during migration.

This supports costume-by-episode behavior without forcing all scene or prop alternatives to be exclusive forever. A plain episode-number array in JSON was rejected because it cannot enforce tenant isolation, referential integrity, reconciliation, or efficient reverse lookup.

### Introduce an explicit normalization run and candidate boundary

Each recognition attempt creates or reuses an idempotent normalization run linked to the script analysis task, stage, execution, attempt, call log, script version, and raw result. The normalizer performs these ordered steps:

1. Parse and unwrap supported top-level response shapes.
2. Convert documented compatible forms, including string values into `{name: ...}` objects.
3. Validate top-level arrays, non-blank names, field types, length limits, and safe defaults.
4. Produce normalized names, aliases, type-specific metadata, and source indexes.
5. Match candidates within the same tenant, project, and asset type using normalized exact names and explicit aliases; ambiguous fuzzy matches remain separate review candidates.
6. Group duplicates, record the winning canonical candidate and merge evidence, and expose the proposal for review.

Raw provider output is never written directly into canonical asset columns. Invalid candidates fail with a normalized business error and candidate-level diagnostics before draft replacement. Storing only the normalized output was rejected because it would remove the evidence needed to diagnose prompt/schema drift.

### Promotion is transactional and user-controlled

Normalization produces review candidates, not formal canonical assets. Accepting a new candidate creates a canonical asset and optional initial visual variant. Accepting a merge updates the selected canonical asset and attaches approved variants/aliases. Rejecting a candidate leaves canonical data unchanged. Each promotion transaction locks the run/candidate, verifies the current script version and target ownership, writes all affected rows atomically, and records the decision.

High-confidence automatic grouping is allowed inside the candidate set, but automatic destructive merging into confirmed assets is not. This preserves the existing confirmation boundary while making match semantics stronger than exact raw-name equality.

### Extend APIs additively during migration

Existing workspace fields remain available. Asset responses gain `variants`, `primaryVariantId`, normalization status, and episode-binding summaries; episode responses gain stable ids. New focused endpoints manage candidate decisions, visual variants, primary selection, and episode bindings. Existing current-image fields continue to be populated/read until all consumers resolve through variants.

The workbench presents canonical cards with shape counts and generation status, a normalization review queue, and episode bindings. Storyboard and media-generation consumers resolve a variant through the shared resolver rather than duplicating fallback rules.

### Tighten Agent output contracts but retain defensive normalization

The character/scene/prop Agent output schema and rendered prompt explicitly require object arrays and non-empty names. Runtime validation remains authoritative because JSON mode and example schemas do not guarantee provider conformance. Prompt-only enforcement was rejected because the observed production response was valid JSON but structurally insufficient.

## Risks / Trade-offs

- [Polymorphic variant references cannot have a single database foreign key] → Validate ownership in one service, use composite indexes, and add orphan-detection migration tests.
- [Episode reconciliation may bind to the wrong episode after major rewrites] → Use conservative matching, retire ambiguous mappings, surface them for review, and never match only by ordinal position.
- [Normalization rules may over-merge distinct entities] → Restrict automatic matches to deterministic normalized names/aliases and require explicit confirmation for ambiguous cases.
- [Dual-reading legacy image fields and variants increases temporary complexity] → Centralize fallback in one resolver, backfill primary variants, instrument legacy fallback use, then remove it in a later change.
- [Large scripts may create many candidates and bindings] → Batch normalization writes, index by project/type/name and episode, and paginate review APIs.
- [Concurrent retries may duplicate candidates or variants] → Use run idempotency keys, unique source-candidate keys, and conditional promotion updates.

## Migration Plan

1. Add episode, normalization, visual-variant, and episode-binding tables plus additive response fields.
2. Backfill stable episodes from the current valid split result or deterministic parser without changing script content.
3. Backfill one primary visual variant for each existing asset that has a current image/result reference; retain legacy columns.
4. Deploy normalization and review services behind the existing analysis/extraction flows, initially with additive workspace responses.
5. Enable variant/binding management and switch storyboard/media resolution to the shared fallback resolver.
6. Monitor rejected candidates, ambiguous episode reconciliation, orphan checks, and legacy-image fallback usage before making variants authoritative.

Rollback disables new write paths and returns consumers to legacy image fields. Additive tables and columns remain intact so accepted user data is not destroyed. Database migrations do not remove legacy columns in this change.

## Open Questions

- Whether scene and prop variants need shot-level bindings in addition to episode-level bindings is deferred; the schema should permit a later, separate binding scope without changing current episode semantics.
- Product policy must decide whether low-confidence normalization candidates block the entire recognition stage or allow valid sibling candidates to be reviewed while the run is marked partially invalid.
