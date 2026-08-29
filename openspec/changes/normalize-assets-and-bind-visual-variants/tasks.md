## 1. Schema and Domain Foundations

- [x] 1.1 Add a Flyway migration for stable `script_episode` identities, lifecycle fields, reconciliation indexes, and tenant/project/script constraints.
- [x] 1.2 Add normalization-run, normalized-candidate, alias/match-evidence, and promotion-decision tables with execution, attempt, call-log, task, stage, and script-version links.
- [x] 1.3 Add `asset_visual_variant` and `asset_visual_variant_episode` tables with primary/preferred uniqueness, generation lifecycle fields, soft deletion, and lookup indexes.
- [x] 1.4 Add MyBatis entities, mappers, and typed enums/value objects for episodes, normalization records, visual variants, and episode bindings.
- [x] 1.5 Extend schema migration and snapshot tests to verify constraints, indexes, tenant isolation columns, and additive compatibility with legacy asset image fields.

## 2. Stable Episode Identity

- [x] 2.1 Write failing service tests for explicit-heading reconciliation, unchanged intelligent splits, reordered episodes, added/removed episodes, and ambiguous rewrites.
- [x] 2.2 Implement episode fingerprinting and conservative reconciliation using explicit heading identity before normalized content anchors.
- [x] 2.3 Persist reconciled episodes for deterministic parsing and successful intelligent splitting while retiring removed identities without hard deletion.
- [x] 2.4 Extend workspace episode responses with stable episode ids while preserving episode number, title, content, summary, and existing response fields.
- [x] 2.5 Integrate reconciliation into ordinary script saves and analysis completion so unchanged episodes retain bindings across versions.

## 3. Recognition Output Contract and Normalization

- [x] 3.1 Write failing normalizer tests for canonical object arrays, string arrays, supported wrappers, `locations`, `key_items`, missing names, wrong field types, duplicates, aliases, and length limits.
- [x] 3.2 Expand the character/scene/prop Agent output schema and rendered prompt so object fields, non-blank names, empty-value conventions, and allowed top-level fields are explicit.
- [x] 3.3 Update prompt rendering to include the published Agent output schema and ordered Skill content without exposing administrative or secret configuration.
- [x] 3.4 Implement a typed recognition shape adapter that unwraps supported response forms and converts compatible scalar values into named candidates.
- [x] 3.5 Implement required-field, type, length, enum/default, and collection validation before any canonical asset persistence.
- [x] 3.6 Implement deterministic name normalization, explicit alias matching, duplicate grouping, and evidence recording scoped by tenant, project, and asset type.
- [x] 3.7 Persist raw response linkage, normalized output, candidate diagnostics, and run status idempotently for each recognition attempt.
- [x] 3.8 Update recognition-stage success/failure handling so normalization and candidate persistence complete before stage success and business failures retain provider-call evidence.

## 4. Candidate Review and Canonical Promotion

- [x] 4.1 Write failing transactional tests for accepting new candidates, merging into confirmed targets, rejecting candidates, retrying decisions, stale script versions, and cross-tenant targets.
- [x] 4.2 Replace direct analysis/extraction writes to asset tables with normalized candidate replacement while preserving confirmed canonical assets and variants.
- [x] 4.3 Implement candidate-list and detail queries with validation errors, normalized values, aliases, grouping, proposed targets, confidence/evidence, and source references.
- [x] 4.4 Implement atomic accept-new, accept-merge, retarget, and reject decisions with row locking, idempotency, ownership checks, and audit records.
- [x] 4.5 Preserve compatibility for existing `PENDING_REVIEW` and `merge_target_id` flows while routing new normalization decisions through the promotion service.

## 5. Logical Assets and Visual Variants

- [x] 5.1 Write failing variant lifecycle tests for create, update, soft delete, primary replacement, invalid polymorphic ownership, generation success, and generation failure.
- [x] 5.2 Implement the shared visual-variant ownership service for character, scene, and prop canonical assets with tenant/project/type validation.
- [x] 5.3 Implement primary-variant selection and usable-media checks independently from canonical asset confirmation status.
- [x] 5.4 Link AI image task creation and completion to visual variants while retaining current legacy asset result updates during migration.
- [x] 5.5 Backfill one primary variant for existing assets with usable current image references without changing canonical asset ids.

## 6. Episode Bindings and Downstream Resolution

- [x] 6.1 Write failing binding tests for batch assignment, preferred replacement, alternatives, cross-project rejection, retired episodes, and ambiguous reconciliation.
- [x] 6.2 Implement variant-to-episode binding commands and queries using stable episode ids and one preferred variant per canonical asset and episode.
- [x] 6.3 Implement a shared episode-aware visual resolver with preferred binding, primary variant, and legacy media fallback order.
- [x] 6.4 Update storyboard image/video preparation and other asset consumers to use the shared resolver instead of reading canonical asset image fields directly.
- [x] 6.5 Add diagnostics for unresolved or retired bindings and instrument remaining legacy fallback usage.

## 7. Backend API and Authorization

- [x] 7.1 Add response contracts for canonical assets with variant counts, primary variant, generation summary, episode bindings, and normalization review status.
- [x] 7.2 Add authorized endpoints for normalization review decisions, visual-variant CRUD/primary selection, and episode-binding batch updates.
- [x] 7.3 Enforce tenant membership, project access, element permissions, script ownership, and stable-episode ownership on every new read and mutation endpoint.
- [x] 7.4 Add controller and integration tests for additive workspace compatibility, pagination, error responses, idempotency, and cross-tenant isolation.
- [x] 7.5 Regenerate the frontend OpenAPI client and typings rather than editing generated service files manually.

## 8. Production Workbench UI

- [x] 8.1 Add frontend tests for canonical asset cards, visual-variant counts/status, primary indicators, empty states, and legacy fallback display.
- [x] 8.2 Build the normalization review UI for invalid candidates, duplicate groups, proposed merges, explicit retargeting, acceptance, and rejection.
- [x] 8.3 Build visual-variant management for characters, scenes, and props, including create/edit, generate/regenerate, primary selection, and failure display.
- [x] 8.4 Build episode-binding controls that display stable episode number/title selections and distinguish preferred from alternative bindings.
- [x] 8.5 Update storyboard/reference selection to show the resolved episode variant and its fallback source without breaking existing project workspaces.
- [x] 8.6 Run `npx antd info` for every newly used Ant Design component and add locale strings for all supported frontend locales.

## 9. Migration, Verification, and Rollout

- [x] 9.1 Add data migration rehearsal tests for existing confirmed, draft, pending-review, merged, deleted, and image-backed assets plus projects with deterministic and AI-split episodes.
- [x] 9.2 Add end-to-end backend coverage proving malformed AI output cannot produce a null-name insert and valid string-array output becomes reviewable named candidates.
- [x] 9.3 Add end-to-end coverage proving a character costume bound to selected episodes is resolved by storyboard/media workflows while other episodes use the primary variant.
- [x] 9.4 Run focused backend tests, the complete backend test suite, frontend type checks, Jest tests, Biome lint, and Ant Design lint; resolve all regressions.
- [x] 9.5 Document rollout flags, backfill procedure, orphan/ambiguity queries, observability metrics, legacy fallback monitoring, and non-destructive rollback steps.
