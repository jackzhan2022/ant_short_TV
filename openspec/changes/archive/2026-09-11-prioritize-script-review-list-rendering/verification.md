# Production MySQL Read-Path Evidence

Sampled on 2026-09-12 against the production `ant_short_tv` database using a
read-only JDBC session. The representative tenant had seven active review
projects. The focused summary and metrics reads do not return script content,
review reports, evidence, excerpts, problems, or suggestions; the legacy
full-entity path comparison below documents why that distinction matters.

| Read | Statements | Result | `EXPLAIN ANALYZE` observation |
| --- | ---: | --- | --- |
| Summary projection | 1 | 7 rows | `idx_review_project_list_summary` reverse range scan; database execution 0.0401 ms. |
| Version counts | 1 | 7 grouped rows | `uk_review_version` range scan over the visible project IDs; database execution 0.0469 ms. |
| Latest task projection | 1 | 6 rows | `uk_review_task_idempotency` lookup plus `idx_review_task_project_latest` covering anti-join; database execution 0.132 ms. |
| Issue counts | 1 | 3 grouped rows | `uk_review_issue_no` range scan over the latest task IDs; database execution 0.0491 ms. |

The focused metrics path therefore uses four narrow statements after access
selection: visible projects, version counts, latest tasks, and issue counts.

## Legacy Full-Entity Read Comparison

The following production database comparison completed task 7.3 on 2026-09-12.
It used tenant 1 for the database plans: seven active, unbound review projects
(`2, 3, 4, 5, 7, 8, 9`), nine versions, fifteen tasks, and six latest task IDs
(`8, 18, 19, 27, 29, 30`). The issue query returned ten issues. The separate
authenticated HTTP sample below used tenant 7 because that browser session had
one accessible, non-empty project; the two tenant IDs are deliberately not
presented as the same sample.

### Query-count and column boundary

Counts below describe business data statements after tenant/current-user
authentication. Every endpoint first validates the tenant and active member.
For a non-owner, resolving tenant-wide permissions also reads the member's RBAC
data. Those shared authorization reads are not counted as list aggregation
statements. Legacy access can additionally perform project authorization per
bound project, whereas the new paths use one set-based visible-project
selector. Tenant 1 has no bound projects, so the four legacy aggregation
statements are directly comparable without that variable per-project branch.

| Read | Fixed data statements | Selected data boundary | Representative result |
| --- | ---: | --- | --- |
| Legacy `GET /projects` | 4 core statements, plus variable authorization reads for bound projects | Full mapped `review_project`, `review_script_version`, `review_task`, and `review_issue` entities | 7 projects, 9 versions, 15 tasks, 10 latest-task issues |
| `GET /projects/summaries` | 1 | `review_project`: `id`, binding, name/source, current version, status, and timestamps only | 7 visible rows |
| `GET /projects/metrics` | 4 | Visible-project projection; grouped `project_id`/count; latest task identity/state fields plus a Boolean report-present test; grouped task issue counts | 7 project rows, 7 version groups, 6 latest tasks, 3 issue groups |

Thus the progressive first paint is one narrow data statement. When a page also
requests metrics, the two independent endpoints issue five fixed narrow data
statements in total (one summary selector plus four metrics statements); this
is intentionally more round-trip work than the legacy endpoint's four core
aggregation statements, but it removes full-entity materialization and keeps
metrics outside the time-to-summary critical path.

Legacy mapper calls use MyBatis `selectList` for all mapped fields. On the
representative rows, the known unnecessary long-text fields selected by those
four entity reads totalled 2,766,861 bytes. The aggregate values below were
computed in production with `OCTET_LENGTH`; no source text or response body was
retrieved or recorded.

| Legacy entity read | Rows returned | Known long-text fields materialized | Aggregate bytes |
| --- | ---: | --- | ---: |
| `review_project` | 7 | `original_content` | 1,016,368 |
| `review_script_version` | 9 | `content` | 1,465,634 |
| `review_task` | 15 | `selected_dimensions_json`, `review_scope_json`, `global_index_json`, `result_json`, `report_markdown`, `error_message` | 264,388 |
| `review_issue` | 10 | `position_json`, `excerpt`, `problem`, `evidence_json`, `suggestion` | 20,471 |

By contrast, the summary SQL has an explicit nine-column `review_project`
projection. Metrics selects only `project_id` and counts, six latest-task
fields (`project_id`, task ID, round, status, result format, and a derived
Boolean), and task-level issue counts. It does not return script bodies, task
JSON/report bodies, issue evidence, excerpts, problems, or suggestions.

### `EXPLAIN ANALYZE` observations

The legacy full-entity statements used the following production plans:

| Legacy statement | Plan observation |
| --- | --- |
| Projects | `idx_review_project_list_summary` reverse range scan on tenant/deletion; 7 rows, 0.0661-0.342 ms. |
| Versions | `uk_review_version` range scan; 9 rows, 0.0886-0.270 ms. |
| Tasks | `uk_review_task_idempotency` tenant lookup followed by a global `created_at, id` sort; 15 rows, 0.197-0.249 ms after the sort. |
| Issues | Table scan inspected 34 rows, then filtered to 10 task-matching rows; 0.0216-0.0897 ms. |

Fresh narrow-projection plans on the same rows used `idx_review_project_list_summary`
for seven summary rows (0.0278-0.0401 ms), `uk_review_version` for nine source
rows and seven count groups (0.0262-0.0469 ms),
`uk_review_task_idempotency` plus the covering
`idx_review_task_project_latest` anti-join for six latest tasks
(0.0332-0.132 ms), and `uk_review_issue_no` for ten issue rows and three
groups (0.0274-0.0491 ms). No long-text result column is returned by those
new projections; `report_markdown` is inspected only inside the derived
Boolean expression.

### Authenticated response payloads

The browser p50/p95 HTTP sample below provides the endpoint payload comparison
for an authenticated non-empty tenant. Each of 20 sequential requests per
endpoint returned HTTP 200. The legacy response was only 443 bytes despite
the much larger database-side entity materialization above; summary was 311
bytes and metrics was 195 bytes. This confirms that wire size alone did not
identify the legacy read-path cost.

## Initial Production Evidence Gap (Closed)

The first read-only database session could not authenticate HTTP requests. A
subsequent authenticated browser session supplied the endpoint payloads and
separate p50/p95 values recorded below, while the legacy full-entity database
comparison above supplied the missing selected-column and query-count evidence.
Tasks 7.3 and 7.4 are therefore complete.

## Production Deployment Verification

On 2026-09-12, the backend release was updated from `c536c33` to
`6adc4b6`, which includes the additive summary and metrics routes. The release
was built from a clean `origin/master` worktree; its focused
`ReviewWorkbenchControllerTest` run completed with 12 tests, 0 failures, and
0 errors.

The deployment retained the previous release, switched the versioned `current`
link atomically, and restarted `antv.service`. Flyway applied
`V108__optimize_review_library_progressive_reads.sql` successfully. An
authenticated browser then entered `/script-review`, was redirected to the
library, and completed both summary and metrics rendering without the previous
HTTP 400. Work-state filter buttons became enabled after metrics completed.

At this deployment-verification point, response payload sizes and separate
end-to-end p50/p95 measurements had not yet been collected. They are supplied
by the authenticated sampling section below; the rollout evidence is complete.

## Authenticated Empty-Set HTTP Sampling

An authenticated browser session for a tenant with no accessible review projects
sampled each endpoint sequentially 20 times on 2026-09-12. Latency measures the
browser `request()` promise, including response parsing. Payload size is the
serialized JSON response measured in the browser.

| Endpoint | Latency p50 | Latency p95 | Payload p50 | Payload p95 |
| --- | ---: | ---: | ---: | ---: |
| Legacy projects | 89.5 ms | 120.2 ms | 63 bytes | 63 bytes |
| Summaries | 95.1 ms | 129.7 ms | 63 bytes | 63 bytes |
| Metrics | 94.6 ms | 184.6 ms | 63 bytes | 63 bytes |

The browser successfully loaded summaries and completed metrics enrichment after
the backend deployment. These measurements validate the authenticated empty-set
path only. The representative non-empty HTTP sampling and the legacy database
read-path comparison above supply the broader task 7.3 and 7.4 evidence.

## Authenticated Non-Empty HTTP Sampling

On 2026-09-12, an authenticated browser session selected tenant 7, which has
one active review project (project 10) and its completed review task. Each
endpoint was requested sequentially 20 times through the local frontend proxy
to the production backend. The browser supplied its normal session cookies and
the same `X-Tenant-Id` context used by the frontend request interceptor. The
sampling output recorded only HTTP status, elapsed time, and response bytes;
no request headers, cookies, or response bodies were collected.

All 60 requests returned HTTP 200. Latency is measured from immediately before
`fetch()` until the response body was read as a Blob. Percentiles use the
median for p50 and the nearest-rank 19th sorted value for p95 with n=20.

| Endpoint | Latency p50 | Latency p95 | Maximum | Payload p50 | Payload p95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Legacy projects | 1295.0 ms | 2115.9 ms | 4743.3 ms | 443 bytes | 443 bytes |
| Summaries | 105.25 ms | 128.4 ms | 756.8 ms | 311 bytes | 311 bytes |
| Metrics | 109.0 ms | 117.7 ms | 127.5 ms | 195 bytes | 195 bytes |

For the render-critical summary request, p50 is 91.9% lower and p95 is 93.9%
lower than the legacy projects read. The summary payload is 29.8% smaller than
the legacy payload. The one 756.8 ms summary outlier is retained in the maximum
column; it does not alter the nearest-rank p95 for this 20-sample run.

This fills the authenticated non-empty response-size and separate
time-to-summary/time-to-metrics evidence. Combined with the legacy database
read-path comparison above, it completes task 7.3.

## Browser Metrics Failure and Retry Request Shape

On 2026-09-12, Chrome DevTools request blocking was configured for the local
proxy metrics URL. The initial library load issued one successful `summaries`
request (HTTP 200, 134 ms), while the corresponding `metrics` request was
shown as `(blocked:devtools)`. After request blocking was removed and the
page's metrics-only retry control was used, DevTools showed one additional
`metrics` request (HTTP 200, 98 ms) and no second `summaries` request. The
unrelated account refreshes in the same capture were not part of this flow.

The authenticated operator also confirmed that the summary row remained visible
while metrics were blocked and that the page showed the metrics-only retry
control. Together, the browser observation and Network capture verify that a
metrics failure preserves summary navigation and recovers through one
metrics-only request.

## Legacy Rollback Compatibility

The pre-progressive library frontend at commit `6a5bc0c` was extracted without
altering the current worktree and compiled on local port 8001 with its `/api`
proxy targeting the production backend. Its library code calls only the legacy
`GET /api/script-review/projects` contract. The current production controller
still exposes that route, and its `ReviewProjectSummaryResponse` contains every
field used by the old `ReviewProject` client type. The authenticated non-empty
sampling run above also returned HTTP 200 for this legacy route with a 443-byte
project response.

The local rollback server correctly proxies XHR-shaped API traffic to
production; an unauthenticated probe returned the production JSON HTTP 401,
not a local fallback response. An authenticated browser then opened the old
frontend's project history for "人鱼故事" and loaded its legacy history request
`reviews?page=1&pageSize=20` with HTTP 200. This completes the old-UI rollback
observation without changing the production frontend or backend release.

## Final Frontend Verification

On 2026-09-12, the complete frontend verification required by task 7.2 passed:

- `npm test`: 68 test files and 306 tests passed.
- `npm run tsc`: passed with no type errors.
- `npm run biome:lint`: passed with six pre-existing warnings outside this change.
- `npx antd lint ./src`: passed with 14 pre-existing warnings outside this change.
- `npm run build`: production Webpack build completed successfully.

The untracked `.legacy-rollback` validation fixture contains its own nested
Biome root configuration, so it was moved outside `frontend/` only for the
repository lint invocation and restored immediately afterward without changes.

## Final Backend Verification

On 2026-09-12, the focused review/schema suite passed 130 tests with no
failures or errors, including access parity, fixed query bounds, narrow column
selection, and the V108 composite-index assertions.

The full backend suite initially identified two stale migration snapshot
expectations after V108 and V109. The expectations were updated to the actual
104 successful versioned migrations and latest version 109. Both affected
migration test classes then passed 4 tests with no failures or errors.

A clean full verification with `mvn clean test` passed 945 tests with no
failures or errors and one skipped test (`BUILD SUCCESS`).
