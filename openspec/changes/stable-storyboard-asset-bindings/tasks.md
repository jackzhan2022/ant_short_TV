## 1. Schema And Binding Domain

- [x] 1.1 Add failing migration verification for `storyboard_asset_reference` columns, indexes, storyboard/variant foreign keys, soft retirement, role, status, provenance, ordering, and user-lock fields.
- [x] 1.2 Add the next Flyway migration creating `storyboard_asset_reference` with tenant/project/storyboard lookup and active-order indexes.
- [x] 1.3 Add binding entity, mapper, request, response, and enum/value validation types for asset type, role, resolution status, and source type.
- [x] 1.4 Add repository tests for ordered active reads, replacement retirement, tenant/project isolation, and no per-reference query expansion.
- [x] 1.5 Implement the binding repository operations required by the repository tests.

## 2. Transactional Binding Service And Focused API

- [x] 2.1 Add failing service tests for multiple resolved references, explicit category clearing, cross-project assets, and variants that do not belong to the selected asset.
- [x] 2.2 Implement type-specific asset and variant ownership validation and deterministic default-variant resolution.
- [x] 2.3 Implement transactional complete-list replacement, manual provenance/locking, and synchronized legacy `characters`, `scene`, and `props` display fields.
- [x] 2.4 Add controller tests for authorization, focused replacement response, empty-list clearing, invalid resource isolation, and stable ordering.
- [x] 2.5 Add `PUT /api/projects/{projectId}/storyboards/{storyboardId}/asset-references` and return the updated binding projection.

## 3. Legacy Compatibility And Backfill

- [x] 3.1 Add resolver tests for exact normalized names, unique aliases, supported delimiters, ambiguous aliases, missing names, and already-formal storyboards.
- [x] 3.2 Implement bounded legacy-field compatibility projection that returns transient resolved or `UNRESOLVED` references without blocking reads.
- [x] 3.3 Add an idempotent bounded backfill service and administrative runner with per-batch resolved, pending, unresolved, skipped, and failed counts.
- [x] 3.4 Add backfill tests proving reruns do not duplicate rows, ambiguous names are never guessed, and legacy fields remain unchanged.
- [x] 3.5 Add consistency audit queries or metrics that detect drift between formal bindings and synchronized legacy display fields.

## 4. Storyboard Workspace Projection

- [x] 4.1 Add focused workspace tests requiring ordered `assetReferences` for every returned storyboard while preserving episode/page bounds.
- [x] 4.2 Implement one batched binding and referenced-variant projection for the returned storyboard page without N+1 queries.
- [x] 4.3 Extend storyboard response types and frontend service types with resolved asset, variant, image readiness, role, order, source, lock, and resolution fields.
- [x] 4.4 Extend request timing/query-count tests to cover pages with many storyboards and references.

## 5. Agent Stable-Key Material Contract

- [x] 5.1 Add tool-schema tests for ordered material reference objects containing asset type, stable asset key, optional variant key, role, and source name.
- [x] 5.2 Update the bounded asset catalog to expose only relevant stable asset/variant keys, aliases, episode-preferred state, primary state, and image readiness.
- [x] 5.3 Update storyboard Agent Skill and system instructions to submit structured stable-key references and stop asking the model to perform deterministic bookkeeping.
- [x] 5.4 Extend the storyboard candidate model and parser to retain structured references and exact error paths.
- [x] 5.5 Add tests proving duplicate display names do not cause ambiguity when stable keys are supplied.

## 6. Deterministic Normalization And Severity

- [x] 6.1 Add failing normalizer tests for missing/gapped shot numbers, out-of-range anchors, non-monotonic anchors, duration bounds, missing defaults, and altered or repeated utterances.
- [x] 6.2 Extend deterministic normalization to derive numbering, clamp and order trusted source anchors, inject exact utterances once, and apply configured shot-type and duration defaults.
- [x] 6.3 Add a validation result model that classifies findings as fatal, repairable, or warning and records JSON paths and normalized values.
- [x] 6.4 Reclassify authorization, ownership, and stale fingerprints as fatal; classify mechanical, optional-field, and material-resolution findings as repairable.
- [x] 6.5 Add regression fixtures for project 41 failure cases: ambiguous `Serena` and non-monotonic source anchors.

## 7. Focused Correction And Deterministic Fallback

- [x] 7.1 Add execution tests proving the first repairable response triggers one correction call containing only invalid storyboard items and exact errors.
- [x] 7.2 Add tests proving a repeated repairable validation code invokes fallback and succeeds instead of ending the attempt.
- [x] 7.3 Implement fallback visual descriptions from trusted source text, source-range repair, defaults, unresolved bindings, and asset-pending bindings.
- [x] 7.4 Preserve provider transport retry limits while preventing repairable content findings from restarting all trusted reads.
- [x] 7.5 Expose normalization, corrected-item, fallback-item, unresolved, pending, business-call, and technical-retry diagnostics on executions and storyboard batches.

## 8. Atomic Publication And Manual Lock Preservation

- [x] 8.1 Add transaction tests proving fatal fingerprint or ownership failures preserve every prior storyboard and binding.
- [x] 8.2 Extend formal storyboard publication to insert storyboards, shots, prompt documents, diagnostics, and binding rows in one transaction after all normalization and fallback.
- [x] 8.3 Add regeneration tests for carrying manually locked references across a deterministic source-range match.
- [x] 8.4 Implement explicit material-overwrite admission and ensure ordinary AI regeneration cannot replace locked rows.
- [x] 8.5 Add tests for an unmatched regenerated storyboard warning without attaching old locked references to an unsafe target.

## 9. Prompt And Video Reference Preparation

- [x] 9.1 Add prompt compiler tests for all resolved formal bindings, manual structured Mentions, plain angle-bracket text, and asset-variant deduplication.
- [x] 9.2 Make formal bindings authoritative in deterministic prompt rendering while preserving manual Mention semantics.
- [x] 9.3 Add video-task preparation tests for role/order ranking, image readiness, mixed media, duplicate references, and model limits.
- [x] 9.4 Implement provider-subset selection without deleting storyboard bindings and persist kept/omitted reference diagnostics in the task snapshot.
- [x] 9.5 Extend the Seedance adapter tests and implementation to map the selected multimodal subset in deterministic order.

## 10. Multi-Reference Workbench

- [x] 10.1 Add component tests for adding, removing, and reordering multiple character rows with independent visual variants and voice actions.
- [x] 10.2 Implement character rows with thumbnail, searchable asset selector, variant selector, voice action, status, reorder, and remove controls.
- [x] 10.3 Add component tests for multiple main/supporting/transition scene image cards and independent variant selection.
- [x] 10.4 Implement scene cards with role, searchable asset selector, variant selector, status, reorder, and remove controls.
- [x] 10.5 Add and implement multiple prop rows or cards with searchable assets, independent variants, status, reorder, and remove controls.
- [x] 10.6 Replace legacy single selectors with the focused binding endpoint and update only the affected storyboard card on success.
- [x] 10.7 Add optimistic-update rollback tests proving a failed save restores only the affected card and does not reload the episode.
- [x] 10.8 Add accessible unresolved confirmation and asset-pending image-generation actions.
- [x] 10.9 Display model media limits and kept/omitted provider references without removing saved storyboard bindings.

## 11. End-To-End Verification And Rollout

- [x] 11.1 Add an end-to-end test that generates one episode with multiple characters, scenes, and props, persists independent variants, and prepares a deduplicated video request.
- [x] 11.2 Add an end-to-end fallback test where correction repeats an anchor error and generation still publishes a complete warned episode.
- [x] 11.3 Run focused backend tests, full storyboard Agent and video reference tests, frontend storyboard tests, TypeScript, Biome, and Ant Design lint.
- [x] 11.4 Measure bounded storyboard workspace query counts and compare generation completion, fallback, unresolved, and pending rates against the rollout thresholds.
- [x] 11.5 Deploy schema and backward-compatible backend first, run the bounded backfill and consistency audit, then deploy the frontend.
- [ ] 11.6 Perform authenticated production smoke tests for project 41 generation, manual multi-reference editing, user-lock regeneration, prompt compilation, and video-task reference diagnostics.
- [x] 11.7 Document rollback to the prior application while retaining the additive binding table and synchronized legacy fields.
