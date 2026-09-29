## Verification Evidence

### Automated coverage

- Migration, repository, focused service/controller, compatibility resolver, idempotent backfill, and drift audit tests pass.
- A bounded storyboard page with many references remains at 14 SQL calls; reference count does not add per-storyboard or per-reference queries.
- Agent schema, catalog, normalizer, severity, focused correction, fallback, atomic publication, manual-lock carry-forward, prompt compiler, provider subset, and Seedance adapter tests pass.
- End-to-end fixtures publish six independent character/scene/prop bindings and compile six deduplicated media identities.
- The project 41 regressions for ambiguous `Serena` and non-monotonic source anchors publish an editable result instead of failing for repairable content.
- Frontend storyboard tests cover multi-reference add/remove/reorder, independent variants, roles, voice actions, pending image actions, focused rollback, and provider omission diagnostics.

### Rollout thresholds

| Signal | Initial threshold | Action when exceeded |
| --- | ---: | --- |
| Storyboard workspace SQL calls for a 20-card page | <= 15 | Stop frontend rollout and inspect batch projection |
| Generation completion rate excluding fatal scope/fingerprint failures | >= 99% | Inspect validation/fallback codes before increasing traffic |
| Deterministic fallback rate | <= 10% | Review Agent output and fallback quality samples |
| Unresolved formal binding rate | <= 5% | Review aliases and require manual confirmation for the affected rows |
| Asset-pending formal binding rate | <= 20% | Queue asset-image generation; do not remove bindings |
| Binding/legacy consistency drift | 0 rows | Stop backfill and repair dual-write behavior |

Rates are calculated over completed storyboard runs after deployment. Fatal authorization, execution ownership, and stale-fingerprint failures are reported separately and are never counted as repairable generation failures.

### Production checks

1. Confirm Flyway reaches V122 and the backend starts before deploying the frontend bundle.
2. Run bounded backfill batches and record resolved, pending, unresolved, skipped, and failed counts.
3. Run the consistency audit after every batch; drift must remain zero.
4. Exercise project 41 generation, focused multi-reference editing, locked regeneration, prompt compilation, and video reference diagnostics.
5. Confirm a focused binding save does not issue a storyboard workspace reload.

### 2026-09-29 rollout result

- Release: `/opt/antv/releases/20260929013739-1f30c7a-stable-bindings`
- Flyway: V122
- Backfill: 892 storyboards, 4,423 references, 2,625 pending, 1,798 unresolved, 0 failed
- Consistency audit: 0 drift rows
- Remaining legacy candidates: 0
- Project 41: 279 active storyboards, 1,280 formal references, 278 structured prompt documents
- Service checks: backend active, protected API 401 without a session, public frontend 200
- Frontend bundle: `umi.c5633f77.js`; previous frontend retained as `dist.previous`
- Database backup: `/opt/antv/backups/20260929013739-stable-bindings/database.sql.gz`

Authenticated write smoke remains pending because the Codex browser connector reported `Codex auth token is unavailable`; no production credentials were created or extracted to bypass that boundary.
