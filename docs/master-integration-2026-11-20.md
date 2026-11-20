# Master integration — 2026-11-20

## Scope and recovery

User authorized merging all outstanding branches, pushing origin/master, and switching the working directory to master. No deployment is included.

Original workspace (tracked and untracked) backup: Git stash `0c3a308fa6ffbc199a7ddae570cd951a16a26a2f`. This backup is intentionally retained. Previous master is preserved as `codex/master-before-integration-20261120` at `9b7e7811`.

## Reconciliation

- Workspace: retained newer master implementations where the old working tree overlapped them; integrated Gemini native image support, regression tests, provider scripts and missing records.
- Retired character voice drafts: preserved under `docs/archive/character-voice-draft-20261026/`; their legacy database dependency is not activated.
- `codex/feedback-performance-release`: merged release records; runtime performance work was already in master.
- `codex/video-preview-workbench`: preserved newer drama library, miniapp, permissions and pricing implementations on master. Added the independent trend collection backend, tests, fixtures and jsoup dependency. Renumbered its migrations from V134/V135/V136 to V180/V181/V182 to avoid collisions. Archived superseded miniprogram source under `docs/archive/miniprogram-20261017/`.
- Generated `outputs/`, `.workbuddy/` memories and `docs/previews/` additions from the old video branch remain available in its merge-parent history (`acade713`); they are not added to the active source tree.

- GitHub Actions dependency branch: merged pinned checkout, setup-node, setup-utoo and react-doctor updates at the relocated `frontend/.github/workflows/` paths.
- npm dependency branch: upgrades are applied to `frontend/package.json` and its lockfile. Root `package.json` remains the current multi-service command entry point. Frontend Node engines now match jsdom 30 requirements: `^22.22.2 || ^24.15.0 || >=26.0.0`; verification uses Node 24.22.1.

- `origin/gh-pages`: incorporated the unrelated Git history and preserved its complete latest site tree (`f1b24ab6`, 2026-09-02) under `docs/archive/github-pages-20260902/`; no Pages deployment is performed.

## Validation

- Backend clean compilation and trend tests: 36 tests discovered, 35 passed, 1 live-network test intentionally skipped. Full H2 Flyway migration succeeded through V182 (176 migrations).
- Backend Gemini/OpenAI adapters, drama library/persistence and WeChat authorization regression: 54 passed, 0 failures/errors.
- Frontend final `npm run lint` (Biome + TypeScript): passed; 14 existing warnings and 4 infos. `antd lint ./src`: passed; 8 deprecation and 9 usage warnings (new antd deprecates existing List components).
- Frontend production build on Umi 4.7.34 / Node 24.22.1: passed.
- Dependency compatibility: upgraded pagination moves handlers to accessible controls. Updated three test selectors to click actual buttons; all 32 tests in messages, tenants and commercial orders passed on rerun.
- Baseline comparison at 9b7e7811 using its original dependencies: `src/app.test.tsx` (App context wrapper) and `src/pages/projects/list/index.test.tsx` (member Select mock) already fail. Baseline subset: 43 passed, 2 failed; baseline tenants: 12 passed.
- Archived Pages site verified against all 118 source Git blobs. All 53 local branch refs and 51 remote refs were checked by commit SHA as ancestors of master; Git snapshot/stash recovery refs are intentionally preserved separately.
- Full frontend suite initial run: 185 files / 1516 tests; 180 files and 1511 tests passed, 5 failed, duration 839.86s. Three failures were pagination selectors and are resolved by the 32-test rerun above. The remaining two failures reproduce on the pre-integration master baseline. A second full-suite run was not performed after these test-only selector edits.
