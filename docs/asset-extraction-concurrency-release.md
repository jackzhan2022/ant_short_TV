# Asset Extraction Concurrency Release Runbook

## Verified Before Release

- Backend coordination, persistence, catalog, Agent contract, recovery, and controller suites: 161 tests passed.
- Real MySQL 8/InnoDB concurrency test: passed against isolated randomly named tables; tables were removed afterward.
- Frontend asset settings tests: 22 passed.
- TypeScript: `npm run tsc` passed.
- Biome: no errors; three pre-existing `document.cookie` warnings remain in `requestErrorConfig.test.ts`.
- Ant Design lint: no accessibility or performance issues; eleven pre-existing usage/deprecation warnings remain outside this change.
- Paid provider execution and project 33 production acceptance are intentionally deferred to the release window.

## Release

1. Disable new script analysis asset stages and scoped asset re-extraction submissions.
2. Wait for existing extraction executions to become terminal. Controlled cancellation is allowed, but do not deploy while an old worker can still save assets.
3. Run the read-only duplicate preflight in `scripts/sql/asset-extraction-duplicate-preflight.sql`. Stop if it reports active identity, variant, or binding conflicts; do not merge or delete records automatically.
4. Apply Flyway migration `V116__script_asset_extraction_coordination.sql` and verify the coordinator unique key and `owner_attempt bigint` column.
5. Deploy backend code, frontend code, the four-tool Agent definition, and `backend/skills/short-drama-asset-recognition-framework/SKILL.md` as one release. Mixed old/new extraction workers are not supported.
6. Confirm the recognition Agent snapshot contains, in order, `read_current_episode`, optional `search_script_assets` / `read_asset_details`, and `save_episode_assets`.
7. Re-enable submissions gradually. Observe coordinator ownership, deferred analysis tasks, execution attempts, point reservations, per-episode commit evidence, and final asset counts.

## Project 33 Acceptance

1. Record the current active character, scene, prop, variant, and episode-binding counts for project 33.
2. Run preflight for the intended scope and prompt policy. Confirm the displayed counts and policy before accepting paid work.
3. Submit once. Repeat the same action with a different client key and verify the UI attaches to the same execution with one operation and one point reservation.
4. Submit a different scope or prompt policy while the task is active and verify a conflict references the active task without another reservation.
5. Confirm an episode with the 215+ prop catalog loads successfully, marks the initial catalog incomplete, and can find omitted aliases through global search.
6. Observe every frozen episode reaching committed evidence before finalization. Verify failed, canceled, or stale-source attempts do not retire assets.
7. After success, compare final counts with the baseline. Investigate unexpected removals; manual assets and out-of-scope records must remain.
8. Refresh the asset settings page during execution and verify polling resumes without submitting another paid task.

## Rollback

1. Disable new extraction submissions and drain or controlled-cancel all new-version executions.
2. Roll back backend, frontend, Agent definition, and Skill content together. Do not run old and new extraction workers concurrently.
3. Keep the additive coordinator table and audit records. Do not delete or rewrite business assets, variants, bindings, reservations, or commit evidence.
4. If a coordinator remains owned by a terminal execution, allow the recovery sweep to release it or perform the same owner/version/attempt-conditional release after verifying the execution is terminal.
5. Re-enable submissions only after all workers use the same version and a smoke test confirms one task/reservation for an equivalent request.
