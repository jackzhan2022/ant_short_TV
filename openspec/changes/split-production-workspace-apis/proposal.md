## Why

The production workbench currently loads the complete script, every script version, all assets and their nested visual workspaces, every storyboard, and analysis state through one `script-workspace` request. Large projects therefore pay for unrelated database queries, repeated full-content serialization, and the same oversized response on analysis polling, causing slow page entry and avoidable backend load.

## What Changes

- Introduce page-focused read APIs for the script, asset-settings, and storyboard workbench pages.
- Return only lightweight version metadata in the script-page bootstrap response and load a version's full content on demand.
- Return asset summaries without nested visual variants, bindings, candidate counts, or resolved media, and load a single asset's visual workspace when the user opens it.
- Return storyboards by episode with pagination instead of embedding every storyboard in the shared workspace response.
- Poll a lightweight script-analysis status API while analysis is active instead of repeatedly fetching the complete script workspace.
- Migrate the three production-workbench pages to the focused APIs while retaining the legacy `script-workspace` contract during a compatibility period.

## Capabilities

### New Capabilities

- `production-workspace-query-boundaries`: Defines page-focused bootstrap responses, on-demand detail loading, episode-scoped storyboard pagination, and legacy compatibility for production-workbench reads.

### Modified Capabilities

- `script-analysis-progress`: Changes active-analysis refresh behavior so the UI restores and polls persisted progress through a lightweight status endpoint without reloading unrelated workbench data.

## Impact

- Backend controllers, response DTOs, and query services under `backend/src/main/java/com/antshorttv/script/`.
- Frontend production-workbench services and the script, settings, storyboard, video, and shared-header consumers under `frontend/src/pages/projects/production-workbench/`.
- Backend controller/service tests and frontend Vitest coverage for request boundaries, pagination, lazy loading, polling, errors, and compatibility.
- No new runtime dependency and no immediate breaking API removal; `GET /api/projects/{projectId}/script-workspace` remains available until all known consumers have migrated.
