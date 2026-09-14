## Context

The script review UI currently treats the project detail response as a shared data source for the library and the workbench. The library first lists accessible projects and then requests every project's complete detail to derive display states. The workbench requests the project list, selected project detail, version history, and selected task detail during initial entry. Backend response mapping compounds this with per-project version/task queries and per-task/per-issue child queries.

Production profiling with five visible review projects showed six review requests on library entry and up to 4.65 seconds for the slowest project detail. Opening a review round loaded approximately 95 KB of project detail and 183 KB of version history before the page finished its initial data sequence. The existing authorization distinction between creator-owned unbound drafts and main-project-bound reviews must remain intact.

## Goals / Non-Goals

**Goals:**

- Preserve the current script-library layout, search, state filters, and import interaction.
- Establish three navigation levels: review-project library, per-project review history, and per-task review detail.
- Give each level an explicit read contract that does not preload data owned by the next level.
- Return permission-scoped library summaries without frontend N+1 detail requests or backend per-project aggregate queries.
- Show every review attempt as an independent history row, including running, failed, canceled, and completed states.
- Load exactly one selected task and its bound script version on the detail page.
- Preserve existing review actions and authorization behavior.

**Non-Goals:**

- Redesigning the current library presentation.
- Changing review execution, issue matching, persistence statuses, or workflow-Agent behavior.
- Changing the review database schema.
- Combining library, history, and detail data into one configurable or polymorphic endpoint.
- Adding infinite scrolling or changing the existing client-side library filters.

## Decisions

### 1. Use page-scoped REST read contracts

The canonical read flow will be:

- `GET /api/script-review/projects` for the current library.
- `GET /api/script-review/projects/{projectId}/reviews` for one project's review-history page.
- `GET /api/script-review/tasks/{taskId}` for one review-detail page, including only that task's bound version content and formal review details.

The project-list response will add server-derived `reviewState`, `outstandingIssueCount`, and latest-round metadata so the frontend can render and filter the current library without requesting project details. The history response will contain project header metadata, version metadata needed by review creation, and paged lightweight task rows; it will not contain version bodies, issues, hits, fanout units, or observability details. The task-detail response will contain the selected task, its bound version content, issues, hits, progress, fanout, and observability, but no other task or version bodies.

This is preferred over a batch-detail endpoint because batching full details would reduce HTTP count while retaining over-fetching. It is preferred over `include=` expansion parameters because those combinations would recreate an unbounded all-in-one contract.

### 2. Make history the navigation boundary between projects and tasks

Selecting a row in `/script-review-library` will navigate to `/script-review/projects/{projectId}/reviews`. Each review attempt is a separate history row ordered by creation time descending, even when multiple attempts use the same script version. Selecting a row navigates to `/script-review/tasks/{taskId}`.

The history row includes task/round identity, script version number and file name, review mode, scope, selected dimensions, status, overall progress, issue totals, unresolved totals, creator, creation time, completion/cancellation time, and a concise failure message where applicable. Status filters include pending/running, failed, canceled, and completed attempts. A project with no review attempts shows an empty state and retains the action for starting its first review.

Existing `/script-review?projectId=...` links will redirect to the project's history route. Links that already identify a task will redirect to the task-detail route where feasible.

### 3. Replace mapping-time queries with explicit batch queries

The library service will first resolve the accessible review-project set using existing access rules, then obtain version/task/issue aggregates for those project IDs with a fixed number of batch queries. Response mapping will be pure and MUST NOT issue mapper calls.

The history query will fetch task rows and task-level issue counts using aggregate SQL rather than loading issue entities. It will be pageable with a default page size of 20 and stable ordering by creation time and ID descending. Version metadata will exclude script content.

The task-detail query will fetch issues and hits in batches and group them in memory by task/issue ID. Fanout cache usage will likewise be loaded for all relevant run IDs in one operation rather than once per unit. These changes bound database query count independently of project, issue, hit, and fanout-unit counts.

### 4. Preserve action behavior while refreshing only the current level

Library import refreshes the lightweight library response and navigates to the new project's history page. Review creation occurs from the history page using version metadata and navigates to or follows the created task. Retry and cancellation refresh the selected task detail and its corresponding history row when the user returns. Issue resolution and batch repair refresh only the selected task detail; version-changing actions return to or refresh the project history when a new review version must become selectable.

Errors are scoped to the active level: a library failure shows a library error state, history failure preserves the project route with retry, and detail failure does not trigger fallback requests for another project or task. Authorization failures remain 403 and cross-tenant identifiers remain fail-closed.

### 5. Verify request shape and query bounds, not only rendered output

Frontend tests will assert that library entry sends no per-project detail calls, history entry sends no task-detail calls, and detail entry requests only the selected task. Backend tests will assert response-field boundaries, access filtering, stable ordering, and query behavior for multiple projects/tasks/issues. Performance-oriented integration tests will seed multiple records and verify that response query counts remain bounded as record counts increase.

## Risks / Trade-offs

- **[Risk] Summary state can drift from the current client derivation** → Define one server-side state derivation with parity tests for every current library state before removing the client aggregation.
- **[Risk] Splitting routes can break saved links** → Keep compatibility redirects for existing project-based workbench URLs.
- **[Risk] Task detail remains large for very long scripts or many findings** → Return only the bound version and selected task, preserve gzip, and keep issue rendering independently pageable if measured rendering requires it.
- **[Risk] Aggregate queries can become complex** → Keep access resolution separate from aggregate loading and test empty, creator-owned, project-bound, and tenant-wide access cases.
- **[Risk] Current actions span history and detail concerns** → Assign each action an owning page and refresh boundary explicitly rather than reintroducing a shared mega-response.

## Migration Plan

1. Add backend summary/history/detail contracts and tests while retaining existing endpoints and response compatibility where required.
2. Change the library to consume only the lightweight project response and verify no detail requests occur.
3. Add the project review-history route and migrate library navigation and post-import navigation to it.
4. Add the task-detail route and move the existing issue-resolution workbench into that page using the selected-task contract.
5. Add compatibility redirects for legacy `/script-review` links.
6. Deploy the backend first, verify the new reads and authorization, then deploy the matching frontend.
7. Roll back the frontend routes first if needed; the additive backend reads can remain deployed. No database rollback is required.

## Open Questions

None. The library retains its current layout, history is one row per review attempt with all statuses, and review details open as a dedicated page.
