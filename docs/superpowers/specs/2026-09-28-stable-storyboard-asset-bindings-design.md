# Stable Storyboard Asset Bindings Design

## Goal

Generate a complete, editable episode storyboard even when model output contains repairable validation errors, while storing every storyboard's character, scene, and prop references as stable asset and visual-variant bindings.

Only authorization failures, stale script or episode fingerprints, and invalid execution ownership may terminate generation without producing storyboards.

## Current Problems

- Storyboards persist `characters`, `scene`, and `props` as display-name strings.
- Similar names and aliases can be ambiguous. Project 41 has failed with `素材名称或别名存在歧义：Serena`.
- Source anchors are supplied by the model and can violate deterministic ordering. Project 41 has failed with `镜头来源锚点必须位于当前分镜范围内并按顺序非递减`.
- One failed validation can fail the whole episode after the correction call.
- Prompt text that looks like `<asset>` is not necessarily a structured media reference.
- The workbench edits only the first character, scene, or prop even when multiple names are stored.

## Data Model

Add `storyboard_asset_reference` with:

- `id`, `tenant_id`, `project_id`, `storyboard_id`
- `asset_type`: `CHARACTER`, `SCENE`, or `PROP`
- `asset_id`
- `variant_id`, nullable while unresolved or while no visual variant exists
- `reference_role`: `VISIBLE`, `MAIN`, `SUPPORTING`, or `TRANSITION`
- `sort_order`
- `resolution_status`: `RESOLVED`, `ASSET_PENDING`, or `UNRESOLVED`
- `source_type`: `AI`, `MANUAL`, or `LEGACY`
- `source_name`, preserving the model or legacy text for review
- `locked_by_user`, protecting manual choices during regeneration
- timestamps and soft-retirement metadata

Foreign keys enforce storyboard, asset, and variant ownership where possible. Service validation additionally verifies that the variant belongs to the selected asset and project.

Keep legacy `characters`, `scene`, and `props` columns during compatibility rollout. Every binding write derives and updates these display-name fields for older consumers.

## API

Storyboard workspace responses include an ordered `assetReferences` array with asset and variant display data, image readiness, source, role, and resolution status.

Add:

`PUT /api/projects/{projectId}/storyboards/{storyboardId}/asset-references`

The request replaces the current storyboard's ordered references transactionally. It only refreshes the affected storyboard card in the client. Empty arrays explicitly clear that asset type.

The existing storyboard update endpoint remains responsible for prose and ordinary storyboard fields.

## Generation Protocol

The storyboard Agent receives a compact asset catalog containing stable `assetKey` values, aliases, and visual `variantKey` values. Each generated storyboard submits ordered structured references rather than display names.

If the script explicitly specifies a look, the Agent may submit its `variantKey`. Otherwise the server resolves a visual in this order:

1. episode-preferred variant;
2. primary usable variant;
3. the only usable variant;
4. primary or only variant without an image, marked `ASSET_PENDING`;
5. no variant, marked `ASSET_PENDING`.

AI regeneration preserves rows with `locked_by_user = true`. An explicit overwrite-materials option is required to replace them.

## Stable Validation Pipeline

Generation runs through five stages before one transactional publish:

1. **Parse**: enforce the tool JSON shape and schema version.
2. **Normalize**: compute bookkeeping fields such as source ranges, anchors, ordering, shot numbering, default duration, and default shot type on the server.
3. **Resolve**: resolve stable asset keys and variants. Unresolved or ambiguous legacy names become `UNRESOLVED` references instead of aborting the episode.
4. **Validate**: classify findings as fatal, repairable, or warning.
5. **Publish**: persist the complete storyboard set and asset references atomically.

Fatal findings are limited to authorization or tenant scope violations, execution ownership loss, and stale script or episode fingerprints.

Repairable findings include invalid or missing source anchors, non-monotonic ranges, missing optional creative fields, duration bounds, unresolved assets, missing variants, and provider reference-count limits.

## Correction And Fallback

The first repairable model response triggers one focused correction call containing only invalid storyboard items and exact error paths.

If corrected output remains repairable, deterministic server fallback completes it:

- clamp and order source anchors from trusted source segments;
- derive missing scene and visible-character candidates from evidence and the asset catalog;
- use source text as the fallback visual description;
- apply configured default duration and shot type;
- persist ambiguous materials as `UNRESOLVED`;
- mark missing images as `ASSET_PENDING`;
- reduce provider-bound references deterministically while keeping all storyboard bindings for editing.

The episode succeeds with warnings after fallback. It does not fail solely for content-quality or material-resolution findings.

## Prompt And Video Generation

Video generation reads `storyboard_asset_reference` as the authoritative material list. It merges structured storyboard references with manual prompt mentions, deduplicates by asset and variant, and records the final provider references.

The workbench shows model reference limits before submission. Exceeding bindings stay saved on the storyboard; the video request selects references by role and order and reports which references were omitted from that provider call.

## Workbench UI

- Characters render as ordered rows with thumbnail, asset selector, visual-variant selector, voice action, reorder, and remove.
- Scenes render as image cards with asset selector, visual-variant selector, role, reorder, and remove.
- Props render as compact ordered rows or cards with asset and visual-variant selectors.
- Each section has an add action with search.
- Saving updates only the affected storyboard card.
- `UNRESOLVED` rows show the source name and require user confirmation.
- `ASSET_PENDING` rows link to asset-image generation without losing the binding.

## Legacy Migration

Existing storyboards without binding rows are read through a compatibility mapper:

- split legacy names on supported delimiters;
- resolve exact normalized names and unique aliases;
- create transient `LEGACY` references;
- preserve ambiguous entries as `UNRESOLVED`;
- persist formal rows on the first user save or controlled backfill.

No legacy ambiguity blocks page loading or video prompt editing.

## Observability

Generation results expose:

- normalization count and paths;
- corrected storyboard count;
- deterministic fallback count;
- unresolved and asset-pending references;
- provider references kept or omitted;
- business model calls and technical retries.

## Testing

- migration and repository isolation tests;
- multiple character, scene, and prop bindings with independent variants;
- cross-project asset and mismatched-variant rejection;
- manual locking across AI regeneration;
- legacy exact, alias, ambiguous, and missing resolution;
- ambiguous names and invalid source anchors succeeding with warnings;
- malformed shape receiving one correction call, then deterministic fallback;
- fatal fingerprint and ownership errors still rolling back;
- prompt compilation using all resolved references without duplicates;
- provider-limit selection without deleting storyboard bindings;
- workbench add, remove, reorder, variant selection, local refresh, rollback, and accessibility tests.

## Rollout

Deploy database schema and backward-compatible backend readers first. Backfill in bounded batches with metrics, then deploy the multi-reference workbench. Keep legacy columns synchronized until all production consumers read structured bindings.

