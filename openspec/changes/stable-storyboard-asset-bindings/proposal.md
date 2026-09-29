## Why

Storyboard generation currently depends on display-name strings and model-supplied bookkeeping fields, so ambiguous names and correctable source-anchor errors can fail an entire episode after the single correction attempt. The workbench also edits only one character, scene, or prop even when the storyboard content uses several, which makes generated material references incomplete and unreliable for video generation.

## What Changes

- Persist ordered storyboard-to-asset and visual-variant references independently from legacy display-name columns.
- Allow each storyboard to bind multiple characters, scenes, and props, each with its own visual variant, role, order, resolution status, provenance, and user lock.
- Add a focused endpoint and workspace response contract for reading and replacing one storyboard's material references without reloading an episode page.
- Change storyboard Agent output to use stable asset and variant keys and persist unresolved or image-pending references without failing the episode.
- Split validation outcomes into fatal, repairable, and warning classes; reserve fatal termination for authorization, ownership, and stale-source violations.
- Normalize source anchors, numbering, ordering, durations, and defaults deterministically on the server.
- After one targeted model correction, complete remaining repairable content with deterministic fallback and publish the episode with diagnostics instead of failing it.
- Preserve user-locked material choices during AI regeneration unless an explicit material-overwrite option is selected.
- Compile video prompts and provider inputs from structured bindings plus manual prompt mentions, deduplicating and applying model reference limits without deleting storyboard bindings.
- Read legacy name fields through a compatibility resolver and backfill formal bindings incrementally while keeping legacy columns synchronized.
- Add workbench controls for adding, removing, ordering, and choosing variants for multiple characters, scenes, and props.

## Capabilities

### New Capabilities

- `storyboard-asset-bindings`: Ordered, independently editable character, scene, and prop bindings with visual variants, resolution state, provenance, compatibility mapping, and focused APIs.

### Modified Capabilities

- `short-drama-storyboard-agent`: Stable-key material output, deterministic normalization, validation severity, focused correction, fallback completion, user-lock preservation, and successful publication with repair diagnostics.
- `production-workspace-query-boundaries`: Bounded storyboard workspace responses include structured binding summaries and support card-local material updates without reloading the episode page.
- `volcengine-seedance-video-generation`: Video provider requests consume the selected structured storyboard references, deduplicate them, enforce model limits deterministically, and expose omitted-reference diagnostics.

## Impact

- Adds a Flyway migration and repository/service layer for storyboard material references.
- Changes storyboard Agent tool schema, normalizer, validator, persistence, retry/fallback behavior, and diagnostics.
- Extends storyboard workspace and video-task contracts while keeping legacy fields compatible.
- Updates the production workbench storyboard UI and focused tests.
- Requires a staged backend-first rollout, bounded compatibility backfill, then frontend rollout.
