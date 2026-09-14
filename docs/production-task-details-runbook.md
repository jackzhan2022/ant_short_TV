# Production Task Details Runbook

## Scope

This release adds read-only production-task detail endpoints and the task-center detail drawer. It does not add database migrations, snapshots, backfills, media ingestion, AI calls, billing calls, or changes to task submission, execution, callback, result persistence, or control paths.

The summary endpoints remain lightweight:

- `GET /api/tenants/{tenantId}/production-tasks`
- `GET /api/tenants/{tenantId}/production-tasks/summary`
- `GET /api/tenants/{tenantId}/production-tasks/{taskKey}`

Detail content is loaded only for the selected task:

- `GET /api/tenants/{tenantId}/production-tasks/{taskKey}/content`
- `GET /api/tenants/{tenantId}/production-tasks/{taskKey}/content/{sectionKey}`

Text previews are limited to 4,000 characters, text chunks to 32,000 characters, and collection pages to 20 by default and 100 at most. The endpoint never accepts a table name, storage path, or arbitrary proxy URL.

## Access Boundary

Every detail, section, continuation, and media request checks current active membership, task visibility, tenant ownership, and the source-domain permission. A team owner or system administrator may see a team task summary without gaining access to a project draft, another creator's unbound task, private execution input, media, or downloads.

Stored user-facing prompt fields and result references are returned only when their task/version/run ownership is provable. Missing historical content is returned as `NOT_RECORDED`; current project content is never substituted. Restricted sections contain no hidden title, version, count, URL, or raw error details.

## Release Validation

1. Run `mvn test -Dtest=ProductionTaskControllerTest` in `backend` and `npm test -- --run src/pages/tasks/index.test.tsx` in `frontend`.
2. Verify the list view does not request `/content` until a task drawer opens.
3. Verify a long saved prompt uses bounded continuation and copy-full-text requests.
4. Verify image and video rows reference only task-owned resources; opening a preview must not eagerly fetch video bytes.
5. Verify a member whose project permission is revoked sees neither prior detail content nor a recoverable continuation URL.
6. Verify a shared storyboard batch shows its submitter context while only execution-proven storyboard output is readable.

## MySQL Acceptance And Rollback

Before release, fix the sample task set, concurrent browser load, list/detail response-size budget, and permitted submission and production-stage latency/throughput regression. Compare the baseline to concurrent detail browsing in an isolated MySQL environment. Capture query plans and actual timings for list, first content response, collection continuation, and text continuation. Detail overload or timeout must return a retryable local failure and must not delay task submission or execution.

The release is application-compatible because it adds no schema. To roll back, deploy the preceding backend and frontend release using the standard release symlink procedure in `docs/antv-deployment-runbook.md`. Retain all existing task and result data; do not delete tables, media, or business records for this feature.
