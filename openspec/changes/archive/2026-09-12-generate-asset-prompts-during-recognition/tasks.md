## 1. Extend the Asset Recognition Contract

- [x] 1.1 Add optional, length-bounded `prompt` fields to all five `save_episode_assets` item Schemas while preserving compatibility for payloads that omit them
- [x] 1.2 Expose `hasPrompt` for canonical assets and visual variants in the compact asset catalog without exposing full prompt text
- [x] 1.3 Add Schema and catalog contract tests for accepted prompts, length rejection, omitted prompts, and `hasPrompt` serialization

## 2. Persist Prompts Transactionally

- [x] 2.1 Parse prompt values and conditionally require a non-empty prompt for new records and matched records reported with `hasPrompt=false`
- [x] 2.2 Persist canonical asset and visual-variant prompts in the existing save transaction, filling only empty prompt columns and preserving every existing non-empty value
- [x] 2.3 Add persistence tests for new prompt inserts, empty-value fills, non-overwrite behavior, invalid-payload rollback, and concurrent first-writer preservation

## 3. Update Asset Recognition Generation

- [x] 3.1 Finalize the recognition Skill templates for canonical characters, scene four-view grids, prop-only descriptions, character-look deltas, and prop-state deltas
- [x] 3.2 Enforce Skill guidance for verbatim evidence, explicitly unspecified identity-sensitive fields, allowed visual completion, duplicate-look avoidance, and compatibility with Schemas that do not yet expose `prompt`
- [x] 3.3 Update the Agent flow to use catalog `hasPrompt` values and submit prompts only for new or missing-prompt records in the same `save_episode_assets` payload
- [x] 3.4 Raise the asset-recognition maximum output budget to at least 16384 tokens and retain complete-payload validation so truncated output cannot be partially saved
- [x] 3.5 Extend Skill and Agent tests to cover each prompt type, established-prompt omission, missing-prompt generation, unsupported-Schema fallback, and truncated output rollback

## 4. Use Persisted Prompts for Image Tasks

- [x] 4.1 Replace visual-variant prompt derivation with target-aware selection of the applicable persisted canonical or variant prompt
- [x] 4.2 Require non-primary character looks to use their delta prompt unchanged and attach the usable canonical character image as the only reference image
- [x] 4.3 Keep non-primary scene and prop variants as prompt-only generation and reject every target whose required persisted prompt is empty instead of falling back to names or suffixes
- [x] 4.4 Add image-generation tests proving exact prompt preservation, character reference-image selection, no generic suffix concatenation, and actionable missing-prompt errors

## 5. Align Historical Backfill and Frontend Behavior

- [x] 5.1 Replace hard-coded prompt overwrite SQL in `/prompts/ai-generate` with model-result parsing and type-specific persistence for empty prompts only
- [x] 5.2 Add backfill tests proving existing non-empty prompts remain unchanged and an all-populated target completes without writes
- [x] 5.3 Remove client-side visual-variant prompt derivation and display, edit, and submit the persisted prompt used by the server
- [x] 5.4 Add frontend tests for canonical and variant prompt display, unchanged submission, and missing-prompt validation messaging

## 6. Verify the End-to-End Lifecycle

- [x] 6.1 Run focused backend tests for tool contracts, Skill parsing, persistence, workflow backfill, visual variants, and AI image task creation
- [x] 6.2 Run frontend type checking and focused tests for the production workbench asset settings flow
- [x] 6.3 Exercise one recognition payload containing new, missing-prompt, and established-prompt assets and verify atomic persistence plus exact downstream image-task prompt consumption
