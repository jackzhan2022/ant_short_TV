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

## Validation

Validation results will be recorded after all dependency branches are reconciled.
