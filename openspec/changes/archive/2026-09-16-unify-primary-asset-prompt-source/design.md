## Context

Canonical assets and visual variants both have prompt columns. The current workspace fills an empty primary variant prompt by concatenating the canonical prompt, variant name, appearance, and a generic consistency suffix. The UI displays that derived variant value, while image-task preparation ignores it and uses the canonical asset prompt for a primary variant. This creates duplicated state, misleading edits, and no reliable boundary that prevents an asset-recognition model from persisting episode-specific prose as a canonical character prompt.

The existing database model is still useful: primary variant rows own generated images, status, and episode bindings, while canonical asset rows own stable identity. The change therefore adjusts prompt ownership without removing variant rows or columns.

## Goals / Non-Goals

**Goals:**

- Establish one effective prompt source for each generation target.
- Use the canonical asset prompt for every primary/default visual across characters, scenes, and props.
- Use a non-primary variant's own persisted prompt without runtime concatenation.
- Ensure the prompt displayed and edited in the workspace is exactly the prompt used to create an image task.
- Prevent newly generated canonical character prompts from storing transient episode staging.
- Preserve existing image, binding, and generation-history behavior.

**Non-Goals:**

- Removing `asset_visual_variant.prompt` or primary visual-variant rows.
- Automatically rewriting existing malformed prompts.
- Redesigning the visual gallery or image-generation provider integration.
- Inferring or stripping arbitrary natural-language content after it has been manually authored.

## Decisions

### Resolve an effective prompt by visual ownership

The backend will resolve the effective prompt at the visual-variant boundary. For a primary variant it reads the owning canonical asset's prompt; for a non-primary variant it reads the variant prompt. Workspace responses and image-task preparation will use this same resolver.

This keeps the server authoritative and preserves existing response shapes. Returning a separate canonical prompt to the frontend and asking the client to choose was rejected because it would duplicate business rules and allow display/submission drift to recur.

### Route primary prompt edits to the canonical asset

The existing visual-variant update path will treat the `prompt` field as the effective prompt. When the target is primary, it updates the owning canonical asset prompt; when non-primary, it updates `asset_visual_variant.prompt`. Other variant metadata continues to update normally.

Adding a second UI-only save call was rejected because partial failure could update the visible variant but not the canonical prompt, or vice versa.

### Remove runtime prompt derivation

An empty primary prompt is an empty canonical asset prompt and produces an actionable validation error. An empty non-primary prompt also produces an actionable validation error. Neither listing nor generation preparation will synthesize prompt text from names, appearances, canonical prompts, or generic suffixes.

This replaces the older historical fallback behavior. It favors explicit prompt generation and persisted reviewable content over convenient but semantically incorrect strings.

### Validate AI-generated canonical character prompts at the save boundary

The asset-recognition Skill will state that canonical character prompts describe a neutral, reusable character sheet and exclude episode location, action, transient pose/expression/emotion, held props, and camera language. The persistence boundary will validate the deterministic Markdown structure and required character-description fields whenever AI recognition supplies a character prompt that is required or regenerated. Invalid tool input will return a corrective validation message so the workflow agent can retry in the same run.

The server will not attempt broad semantic censorship of free text. Structural validation is deterministic; semantic exclusions are governed by the Skill and covered by contract examples. Heuristic keyword deletion was rejected because it can corrupt legitimate stable appearance descriptions.

### Leave historical duplicate values in place but inactive

Existing primary `asset_visual_variant.prompt` values will remain stored but will no longer be read, displayed, edited, or submitted. This avoids a destructive migration and supports rollback. Non-primary variant prompts remain active.

## Risks / Trade-offs

- [Existing canonical prompts may already contain transient prose] -> Do not silently rewrite user data; expose the canonical value consistently and let scoped `REGENERATE_ALL` or manual editing replace it.
- [Primary update semantics change behind an existing endpoint] -> Add service and controller-level regression tests proving the owning asset is updated and the variant prompt is untouched.
- [Older clients may expect the primary variant prompt column to change] -> Keep the API response shape stable and return the effective canonical prompt, making behavior correct without a client contract expansion.
- [Strict structural validation may increase agent retries] -> Return field-specific corrective messages and validate only AI recognition writes, not arbitrary existing/manual prompts.
- [Removing fallback derivation exposes historical missing prompts] -> Return an actionable error directing users to generate or enter the missing canonical/variant prompt.

## Migration Plan

1. Deploy the updated Skill, prompt validation, effective-prompt resolver, and compatible API behavior together.
2. Deploy the frontend behavior that treats the returned prompt as the effective prompt.
3. Run regression tests against primary and non-primary visuals for all asset types.
4. Leave historical primary variant prompt columns unchanged; no data migration is required.
5. Roll back application code and Skill together if necessary; retained variant prompt data supports the previous behavior.

## Open Questions

None. Existing malformed canonical prompts are intentionally corrected only through manual editing or an explicit scoped regeneration operation.
