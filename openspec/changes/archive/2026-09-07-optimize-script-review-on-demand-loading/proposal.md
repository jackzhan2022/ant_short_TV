## Why

Opening the script review library currently triggers one project-list request followed by one full-detail request per visible project, and entering a workbench eagerly loads project, version-history, task, issue, hit, and script-content data. This N+1 and over-fetching pattern makes navigation take several seconds and scales poorly as the number, length, and review history of scripts grow.

## What Changes

- Keep the current script-library layout and client-side name/status filtering, but make its project-list response contain every field needed to render the list without fetching project details.
- Navigate from a selected library row to a dedicated per-project review-history page instead of directly opening an all-in-one workbench.
- Add a page-scoped review-history query that returns one lightweight row per review round, including running, failed, canceled, and completed tasks, ordered newest first.
- Open a selected review round on a dedicated detail page and fetch its full result only after the user selects it.
- Load only the selected task's bound script version and review details; do not preload other version bodies, task details, issues, or hits.
- Preserve existing import, review creation, retry, cancellation, resolution, repair, rollback, comparison, and export behavior while making their supporting data requests explicit and on demand.
- Preserve current authorization rules for unbound review drafts and project-bound reviews across all three page-scoped queries.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `script-review-library`: Change project discovery from client-side aggregation of full project details to a single permission-scoped lightweight list, and navigate selected projects to a dedicated review-history page.
- `script-review-workbench`: Separate per-project review history from per-round review detail and require each page to load only the data needed for its current level.

## Impact

- Frontend routes and pages under `frontend/src/pages/script-review-library/` and `frontend/src/pages/script-review/` will be reorganized into library, review-history, and review-detail navigation levels.
- Review service types and requests will gain explicit project-summary, review-history, task-detail, and selected-version contracts.
- Backend review controllers, response records, service queries, and mapper methods will be split into page-scoped reads and batch aggregate queries to eliminate N+1 access.
- Existing review and access-control data models remain unchanged; database schema changes are not expected.
- Existing deep links may be retained through redirects or compatibility routing while the new route hierarchy becomes canonical.
