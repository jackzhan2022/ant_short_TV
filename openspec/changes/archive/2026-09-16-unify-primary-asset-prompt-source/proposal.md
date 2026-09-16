## Why

Primary visual generation currently exposes a derived visual-variant prompt in the UI while the image task uses the canonical asset prompt, so users can review or edit text that is not actually submitted. The derivation also persists duplicated prompt content and can amplify malformed AI-generated character prompts that contain episode-specific action, setting, emotion, or camera direction.

## What Changes

- Make the canonical asset prompt the single persisted prompt source for every primary/default character, scene, and prop visual.
- Make primary visual prompt display, editing, and image-task submission read and write the same canonical asset prompt.
- Stop deriving or persisting a separate prompt for primary/default visual variants.
- Keep non-primary character looks and prop states as independent, delta-only variant prompts; keep non-primary scene variants as independent variant prompts.
- Reject newly generated canonical character prompts that do not follow the required character-sheet Markdown contract, and strengthen the recognition Skill to exclude episode-specific setting, action, transient expression/emotion, held props, and camera language.
- Preserve historical primary-variant prompt values for compatibility but ignore them as generation inputs; no destructive data migration is required.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `asset-prompt-lifecycle`: Define canonical prompts as the sole prompt source for primary visuals and require stable, neutral canonical character descriptions.
- `asset-prompt-management`: Make the primary visual editor operate on the canonical asset prompt and remove primary prompt derivation behavior.
- `visual-variant-guided-generation`: Restrict variant prompt behavior to non-primary variants, require validated persisted prompts, and make primary visuals use canonical prompts.

## Impact

- Backend asset recognition validation and the file-backed `short-drama-asset-recognition-framework` Skill.
- Backend visual-variant workspace responses, prompt updates, and image-generation preparation.
- Frontend production-workbench visual generation modal and related service calls/types.
- Existing API behavior changes for primary visual prompt reads and edits, but endpoint shapes can remain compatible.
- Tests for asset persistence, visual variants, image-task creation, Skill contracts, and the production-workbench settings UI.
