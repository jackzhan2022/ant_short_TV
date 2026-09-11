## 1. Backend Contract Tests

- [x] 1.1 Add failing controller tests for script-page workspace, authorized version detail, asset-settings summary, single-asset visual workspace, and episode-paged storyboard endpoints.
- [x] 1.2 Add failing isolation and validation tests for cross-tenant/project resources, invalid asset types, missing resources, and bounded storyboard pagination.
- [x] 1.3 Add a service-level query-boundary test proving asset-summary loading does not expand visual workspaces per asset and keep a compatibility test for the legacy aggregate.

## 2. Backend Focused Reads

- [x] 2.1 Add separate response DTOs and service queries for script-page bootstrap data and lightweight script-version metadata.
- [x] 2.2 Add authorized on-demand script-version detail loading with full content.
- [x] 2.3 Add asset-settings summary loading that returns persisted list/card fields without nested visual expansion.
- [x] 2.4 Add one-asset visual-workspace loading that reuses existing variant, binding, review-state, and resolved-media behavior.
- [x] 2.5 Add episode-scoped, bounded storyboard pagination with episode navigation and total metadata.
- [x] 2.6 Expose the focused controller routes with existing membership and project-permission checks while preserving `script-workspace` unchanged.

## 3. Frontend Service Contracts

- [x] 3.1 Add page-local TypeScript types and request functions for the focused script, version-detail, asset-summary, asset-visual, analysis-status, and storyboard-page contracts.
- [x] 3.2 Add failing frontend service/component tests that assert each page calls only its focused bootstrap endpoints.

## 4. Script Page Migration

- [x] 4.1 Migrate the script page bootstrap to the focused script-page workspace and load full historical version content only when selected.
- [x] 4.2 Replace five-second aggregate polling with current-analysis polling, stop on terminal state, and refresh the focused script workspace at most once when reconciliation is required.
- [x] 4.3 Add tests for active polling, completion/failure transitions, on-demand version loading, and retained shell/error behavior.

## 5. Settings Page Migration

- [x] 5.1 Migrate the settings page to asset-summary loading and adapt list/card state to summary fields.
- [x] 5.2 Load one asset's visual workspace when its gallery/editor opens, with local loading and error states and stale-response protection.
- [x] 5.3 Refresh only the active asset detail and affected summary after visual mutations, with tests covering open, failure, mutation, and refresh flows.

## 6. Storyboard and Video Page Migration

- [x] 6.1 Migrate storyboard bootstrap and episode switching to bounded episode pages while keeping episode navigation independent from the active page of shots.
- [x] 6.2 Add pagination state, stale-response protection, and active-page refresh after storyboard mutations.
- [x] 6.3 Migrate video/shot consumers that currently use `script-workspace` to the smallest focused storyboard read needed for their initial render.
- [x] 6.4 Add tests for episode switching, pagination, optional media-task failures, and mutation refresh behavior.

## 7. Verification and Performance Evidence

- [x] 7.1 Run focused backend and frontend tests, then full backend tests, frontend tests, Biome/type checks, Ant Design lint, and production build.
  - Evidence: verification.md records the full backend run and corrected-failure rerun separately; frontend 300/300, lint/type checks and final build pass. Existing lint warnings are retained and documented.
- [ ] 7.2 Compare authenticated request duration, query count, and response size for project 33 across legacy and focused endpoints and record the results in the change notes or implementation summary.
- [x] 7.3 Verify no known production-workbench page uses `script-workspace` for initial loading or polling, while the legacy endpoint remains contract-compatible.

## 8. Internal Read-Path Optimization

- [ ] 8.1 Add failing service and controller tests proving focused script and storyboard reads use a lightweight episode projection without persisted episode bodies.
- [ ] 8.2 Batch analysis-stage result, Run, fan-out and split reads so focused analysis response query count does not grow with the stage count.
- [ ] 8.3 Reuse one verified tenant/project access context per focused read and retain authorization isolation coverage.
- [ ] 8.4 Add sanitized request-level HTTP and SQL timing observation for selected production-workspace GET routes.
