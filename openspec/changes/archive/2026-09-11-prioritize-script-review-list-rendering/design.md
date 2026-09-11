## Context

The script review library currently uses `GET /api/script-review/projects` as both its discovery response and its source of derived review metrics. Although the frontend now avoids per-project detail requests, the backend implementation still selects complete version, task, and issue entities to calculate counts and the latest state. Those entities contain script bodies, report JSON, evidence, excerpts, and other long-text fields that are never returned by the list response. Bound reviews also pass through project authorization one item at a time.

The visible response is small, but rendering waits for all authorization and aggregation work. The same project query is also used by the "import from reviewed script" picker, so the rollout must avoid breaking an older frontend or another caller while moving first-party consumers to the progressive contract.

## Goals / Non-Goals

**Goals:**

- Render accessible project rows from a fast query that reads only project-list columns.
- Defer version count, latest round, derived review state, outstanding issue count, and state-specific action text to one non-blocking batch request.
- Keep search, opening a project, and starting an import usable while metrics are loading or unavailable.
- Preserve creator-only access for unbound drafts and effective project access for bound reviews without per-project authorization queries.
- Bound query count and avoid selecting long-text content for both stages.
- Permit a backend-first rollout without changing the existing `GET /api/script-review/projects` response immediately.

**Non-Goals:**

- Redesigning the library cards, filters, or import modal.
- Changing persisted review/task/issue statuses or materializing derived library state in the database.
- Changing project history or task-detail loading.
- Adding server-side search, list pagination, caching infrastructure, or a new external dependency.
- Removing the existing project-list endpoint during this change.

## Decisions

### 1. Add two page-scoped reads and retain the legacy list during migration

The frontend will switch from the legacy list to:

- `GET /api/script-review/projects/summaries`: returns `id`, binding/access source, name, source metadata, persisted project status, current version identifier, and timestamps.
- `GET /api/script-review/projects/metrics`: returns one row per accessible project containing `projectId`, `versionCount`, `latestRoundNo`, `reviewState`, `outstandingIssueCount`, and `actionLabel`.

Both endpoints derive their project set from the same bulk access selector. The metrics endpoint intentionally has no caller-provided project IDs: it returns metrics for the current accessible set, avoids URL-size limits, and prevents identifier probing. The frontend merges metrics by project ID and ignores metrics for rows no longer present.

The existing `GET /api/script-review/projects` remains available with its current response while first-party consumers migrate. This additive contract supports backend-first deployment and rollback. Removing the legacy endpoint is a separate compatibility decision.

Alternatives considered:

- Changing `GET /projects` in place would minimize endpoints but makes backend-first deployment unsafe because current clients require metric fields.
- Loading metrics once per row would defer work but introduces an HTTP N+1 pattern and makes latency grow with list size.
- Keeping a single optimized aggregate response would reduce database work, but any slow aggregate would still block the first paint the user explicitly wants to prioritize.

### 2. Make the summary response independent of review history

The summary query selects only columns stored on `review_project`; it does not join or select versions, tasks, issues, or their long-text columns. It performs one bulk authorization decision:

- unbound rows are visible to their creator or a tenant-wide project viewer;
- bound rows are visible through tenant-wide access or active membership in the bound main project.

The authorization selector returns the accessible rows directly, rather than loading every tenant review and invoking `ReviewAccessGuard.canView` for each item. The existing single-resource guards continue to protect detail/action endpoints.

Rows render immediately with stable name/source/time information, a neutral "进入审核" action, and placeholders for metric-backed fields. Search by name and opening a row remain available. Work-state filters and state counters show a loading state until metrics arrive because using a guessed state would be misleading.

### 3. Calculate all secondary fields with aggregate projections

The metrics service uses a fixed set of projection/aggregate queries over the accessible project IDs:

1. version counts grouped by review project;
2. the latest task per project, ordered by `created_at` and `id` descending;
3. total and unresolved issue counts grouped only for those latest task IDs.

Mapper methods return narrow records instead of `ReviewScriptVersionEntity`, `ReviewTaskEntity`, or `ReviewIssueEntity`. Script content, selected-dimension JSON, review-scope JSON, result/report bodies, excerpts, evidence, problems, and suggestions are not selected. The existing review-state rules are applied to these projected fields so UI semantics remain unchanged.

Indexes will match the actual access and aggregation paths: review-project tenant/deletion/update ordering and review-task tenant/project/latest ordering. Existing indexes will be reused for version grouping and issue lookup where their leading columns already match. Migration tests will assert the new index definitions.

### 4. Treat metrics as enrichment, not as a list prerequisite

After summaries resolve, the frontend commits rows to state before requesting metrics. The metrics request then runs once and merges fields by `projectId`. A request-generation token prevents a late response from an earlier refresh overwriting a newer list.

If metrics fail, the summary rows remain visible and navigable, the state-specific controls remain in an explicit unavailable state, and the page offers a metrics-only retry. A full summary failure continues to use the existing library error behavior. Import success refreshes summaries, renders the updated list, starts a metrics refresh, and preserves navigation to the imported project's history.

The reviewed-script picker uses the same summary and metrics reads. Project names are immediately selectable; version counts appear after enrichment and use a placeholder while loading.

### 5. Verify latency boundaries through query and request shape

Backend tests will seed increasing numbers of bound/unbound projects and assert that summary and metrics query counts remain constant. SQL-capture assertions will verify that summary and metrics statements do not select known long-text columns. Authorization cases cover creator-owned drafts, tenant-wide viewers, project members, removed memberships, and cross-tenant data.

Frontend tests will hold the metrics promise pending and assert that rows, search, and navigation are already rendered. They will also cover successful merging, stale-response suppression, metrics failure/retry, and the fixed two-request shape.

## Risks / Trade-offs

- **[Risk] Metric-backed labels visibly update after first paint** → Use fixed-size placeholders and preserve row layout so enrichment does not cause disruptive movement.
- **[Risk] The accessible set changes between summary and metrics requests** → Authorize each request independently and merge only matching IDs; the next refresh reconciles additions/removals.
- **[Risk] Two requests add a small amount of HTTP overhead** → Run only one metrics request for the complete accessible set; the saved database and payload work dominates that overhead.
- **[Risk] Bulk authorization SQL diverges from single-resource guards** → Centralize accessible-project selection behind one service and test it against the existing ownership and project-access scenarios.
- **[Risk] The legacy endpoint continues to consume resources** → Move all first-party callers in this change, mark the endpoint deprecated in API documentation, and measure remaining traffic before a later removal.
- **[Risk] New indexes increase write cost and storage** → Add only indexes that match measured list/latest-task reads and verify execution plans in a production-like MySQL environment.

## Migration Plan

1. Deploy the additive summary and metrics endpoints, bulk access selector, aggregate projections, and indexes while retaining the legacy endpoint.
2. Verify access parity and compare query counts/latency for representative tenants.
3. Deploy the frontend using progressive summaries and metrics for the library and reviewed-script picker.
4. Monitor legacy endpoint traffic and the p50/p95 time-to-summary and time-to-metrics separately.
5. Roll back the frontend to the legacy endpoint if necessary; the additive backend endpoints and indexes can remain safely deployed.

## Open Questions

None. The initial row deliberately uses a neutral action and postpones state filters/counters until enrichment, prioritizing truthful data and immediate navigation over displaying all metadata in the first paint.
