## Why

The current script workflow persists AI-recognized characters, scenes, and props directly into asset rows, coupling logical identity to one visual representation and allowing malformed or duplicate model output to reach persistence. Production workflows need canonical logical assets, reusable visual variants bound to specific episodes, and a normalization review boundary before recognized data becomes part of the formal asset library.

## What Changes

- Separate canonical character, scene, and prop identity from their generated or uploaded visual variants.
- Allow each visual variant to be bound to one or more stable script episodes, including character costume usage by episode.
- Introduce a normalization pipeline that validates required fields, converts compatible response shapes, resolves aliases and duplicates, and produces reviewable merge decisions before formal asset persistence.
- Preserve raw AI output and normalization evidence for diagnostics while preventing invalid records such as unnamed assets from reaching non-null database columns.
- Extend the production workbench contract so users can review canonical assets, visual variants, episode bindings, generation state, and proposed merges.
- Keep existing confirmed assets and current media references compatible during migration; no existing asset is silently merged or deleted.

## Capabilities

### New Capabilities
- `logical-asset-visual-variants`: Canonical logical assets own zero or more independently managed generated or uploaded visual variants.
- `asset-episode-bindings`: Visual variants can be associated with stable script episodes and exposed to downstream storyboard and media workflows.
- `asset-recognition-normalization`: Raw AI recognition output is validated, normalized, deduplicated, and reviewed before canonical assets are created or updated.

### Modified Capabilities
- `script-analysis-pipeline`: Character/scene/prop recognition must retain raw output and complete normalization before producing reviewable asset candidates.
- `script-element-workflow-boundaries`: Draft replacement and merge preparation operate on normalized candidates and stable canonical identities rather than inserting unchecked provider shapes or relying only on exact names.
- `script-episode-parsing`: Parsed episodes receive stable project-scoped identities so visual-variant bindings survive workspace reads and ordinary script saves when episode identity is unchanged.

## Impact

- Backend schema and migrations for canonical asset metadata, visual variants, episode identities, episode bindings, normalization runs, candidates, and merge evidence.
- Script analysis, element extraction, draft/confirmation, workspace query, image generation, storyboard reference, and retry paths.
- Production workbench APIs and UI for asset review, variant management, binding visibility, generation state, and merge decisions.
- Existing `character_asset`, `scene_asset`, `prop_asset`, `merge_target_id`, AI execution/call-log records, and current image result references require compatibility migration and regression coverage.
