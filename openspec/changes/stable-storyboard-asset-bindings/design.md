## Context

Formal storyboards currently store character, scene, and prop usage as display-name strings and encode actual media identity only when a prompt node happens to be a structured Mention. The storyboard Agent already receives asset keys and variants, but its save path can still fail the whole episode for ambiguous names, model-supplied source-anchor mistakes, duration or numbering errors, and repeated correctable validation codes. Production project 41 contains both failure patterns and formally saved storyboards whose stored material strings disagree with their visual descriptions.

This change crosses Flyway schema, storyboard Agent schema and execution, storyboard persistence, focused workspaces, prompt compilation, video reference resolution, and the React workbench. It must preserve legacy consumers and prior storyboard/media history while rolling out backend-first.

## Goals / Non-Goals

**Goals:**

- Persist ordered, project-scoped storyboard material identity independently from display text.
- Support multiple characters, scenes, and props with independent visual variants and reference roles.
- Make repairable storyboard validation produce a complete editable episode with diagnostics instead of a failed run.
- Keep authorization, execution ownership, and stale-source validation fail-closed.
- Preserve manual material choices across AI regeneration.
- Make video generation consume authoritative structured references and report provider-limit omissions.
- Keep legacy name fields and focused workspace performance compatible during migration.

**Non-Goals:**

- Replacing the canonical character, scene, prop, or visual-variant models.
- Automatically generating missing asset images as part of storyboard generation.
- Rebinding historical generated media to replacement storyboards.
- Removing legacy storyboard name columns in this change.
- Changing provider pricing, reservation, or settlement behavior.

## Decisions

### Use a normalized binding table

Create `storyboard_asset_reference` rather than storing an unvalidated JSON array or extending delimiter-separated strings. Each row stores tenant, project, storyboard, asset type, asset ID, nullable variant ID, role, order, resolution status, source type/name, user lock, timestamps, and retirement state.

The normalized table supports ownership checks, ordered edits, independent variants, bounded reads, and backfill observability. A JSON column was considered but rejected because referential checks, selective migration, and provider reference reporting would remain opaque. Continuing name strings was rejected because it preserves ambiguity and cannot identify variants reliably.

Because `asset_id` is polymorphic across three asset tables, database foreign keys cover the storyboard and variant identities that share one table; service validation enforces the asset table, asset type, tenant, project, and variant ownership relationship.

### Separate material updates from storyboard prose updates

Add a focused replace endpoint for one storyboard's complete ordered reference list. The service validates all rows and writes the replacement plus synchronized legacy `characters`, `scene`, and `props` fields in one transaction.

This avoids overloading the existing storyboard update contract, provides explicit empty-list clearing, and lets the client update one card without reloading an episode. Per-row patch endpoints were considered but rejected because ordering and cross-row validation would require additional concurrency contracts.

### Treat structured bindings as authoritative

Prompt rendering and video-task preparation load formal binding rows first. Manual structured prompt Mentions are merged afterward and deduplicated by media identity. Plain text never recreates a binding.

Legacy strings are compatibility projections, not an identity source after formal bindings exist. Storyboards without rows receive transient legacy references resolved from exact normalized names or unique aliases. Ambiguous and missing names remain explicit unresolved rows when persisted.

### Use stable keys in the Agent contract

The bounded asset catalog supplies stable asset and variant keys. `save_episode_storyboards` schema requires ordered material reference objects with asset type, asset key, optional variant key, role, and source name. The model is not asked to provide database IDs.

When a variant key is omitted, the server chooses episode-preferred, usable primary, only usable, then pending primary/only variant. Missing images do not invalidate the storyboard; they produce `ASSET_PENDING`.

### Move mechanical correctness to deterministic normalization

The model remains responsible for creative grouping, staging, action, emotion, and camera intent. The server derives or repairs source anchors, monotonic ranges, numbering, duration bounds, defaults, trusted utterance injection, and compatibility totals.

This removes model precision requirements that already have a trusted deterministic source. Rejecting these fields for another full model attempt was considered and rejected because production evidence shows correction can repeat the same mechanical error.

### Classify validation by severity

Fatal findings are restricted to authorization or scope violation, lost execution ownership, and stale script or episode fingerprints. These roll back and preserve the prior set.

Repairable findings include source-anchor mistakes, numbering, duration bounds, optional field omissions, ambiguous or missing material identity, missing variants or images, and provider reference limits. Warnings cover quality concerns that do not invalidate storage.

One focused correction call receives only invalid items and JSON paths. Remaining repairable findings are completed deterministically. A successful fallback publishes the complete set with warning diagnostics. This changes the prior rule that a repeated validation code ends the run.

### Publish complete episode results atomically

Parsing, normalization, material resolution, validation, and fallback operate on an in-memory candidate set. The service revalidates the fingerprint under lock, then retires the prior formal set and inserts storyboards, shots, prompt documents, diagnostics, and material references in one transaction.

Valid items are not published early. Partial publication was considered but rejected because episode ordering, coverage, and regeneration behavior would become difficult to reason about.

### Preserve user locks during regeneration

Manual reference changes set `source_type = MANUAL` and `locked_by_user = true`. Regeneration matches replacement storyboards through the existing source/stable identity strategy and carries locked rows forward. The caller must explicitly request material overwrite to replace them.

When no safe match exists, the old storyboard and media remain historical; the new storyboard receives AI bindings and a warning that a manual lock could not be carried forward.

### Apply provider limits at task preparation, not storyboard storage

All storyboard bindings remain stored. Video-task preparation ranks eligible references by role, order, readiness, and media type, then selects the provider-supported subset. Omitted references are recorded in the task snapshot and returned as diagnostics.

Deleting or refusing extra storyboard bindings was rejected because provider choice can change and the storyboard must remain semantically complete.

### Bound focused reads

The existing episode/page query remains bounded. Binding summaries for returned storyboards are loaded in one batched query, and visual detail is projected only for referenced variants. The implementation must avoid per-storyboard or per-reference queries.

## Risks / Trade-offs

- **Polymorphic asset IDs cannot use one direct database foreign key** → enforce type-specific ownership in the transactional service and cover it with cross-tenant/project tests.
- **Legacy names can remain ambiguous** → expose `UNRESOLVED` rows, retain `source_name`, and never guess silently.
- **Fallback can produce lower-quality storyboards** → expose fallback diagnostics and keep every result editable; quality warnings do not masquerade as clean output.
- **Carrying user locks across regenerated storyboard identities can be uncertain** → require a deterministic source-range match and warn rather than attach to a questionable target.
- **More references increase workspace payload and provider validation cost** → use bounded page-level batch projections and prepare provider subsets once per task.
- **Dual writes can drift** → write bindings and legacy display fields in the same transaction and add consistency audits during rollout.
- **Existing prompt documents may contain text that resembles material tags** → only structured Mention nodes or formal bindings carry identity.

## Migration Plan

1. Deploy the table, indexes, repository, compatibility reader, legacy projection synchronization, and backend response fields while the old frontend remains supported.
2. Add metrics for formal, resolved, pending, unresolved, and legacy references.
3. Backfill in bounded idempotent batches. Exact names and unique aliases become resolved rows; ambiguous or missing values become unresolved rows without blocking reads.
4. Deploy the Agent schema, deterministic normalizer, severity classifier, correction/fallback pipeline, and structured persistence.
5. Deploy video-task structured reference preparation and diagnostics.
6. Deploy the multi-reference workbench and focused replace endpoint usage.
7. Monitor fallback rates, unresolved rates, workspace query counts, and generation completion before making formal bindings mandatory for new writes.

Rollback keeps the table and rows in place, restores the previous application release, and continues serving synchronized legacy columns. No rollback step deletes reference data or generated media.

## Open Questions

None. Provider-specific ranking uses existing model constraint configuration, and visual UI details may evolve without changing the binding or generation contracts above.
