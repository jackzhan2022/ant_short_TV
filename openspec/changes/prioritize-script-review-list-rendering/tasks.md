## 1. Lock Down Progressive API Contracts

- [x] 1.1 Add failing `ReviewWorkbenchControllerTest` cases for `GET /api/script-review/projects/summaries` and `GET /api/script-review/projects/metrics`, asserting summary/metrics field boundaries, deterministic ordering, and unchanged availability of the legacy `GET /api/script-review/projects` contract.
- [ ] 1.2 Add failing authorization cases covering creator-owned unbound drafts, tenant-wide viewers, active bound-project members, removed members, inaccessible projects, and cross-tenant rows for both new reads.
- [x] 1.3 Add summary and metrics response records plus controller methods, implementing only enough service delegation for the new contract tests to compile and fail on missing behavior.

## 2. Implement the Render-Critical Summary Read

- [ ] 2.1 Add failing persistence tests proving the summary read returns the same accessible project set for small and larger fixtures without increasing query count per project.
- [x] 2.2 Introduce a focused bulk review-project access selector that expresses unbound creator/tenant-wide visibility and bound main-project visibility in set-based reads while retaining existing single-resource guards.
- [x] 2.3 Implement a narrow summary projection that selects only required `review_project` columns, orders by `updated_at` and `id` descending, and never loads version, task, or issue entities.
- [ ] 2.4 Run the focused backend controller and persistence tests and confirm the summary contract, authorization parity, and constant query bound pass.

## 3. Implement Batch Metrics Without Long-Text Reads

- [ ] 3.1 Add failing service/integration tests for version count, latest-task selection, every existing derived review state, outstanding-issue count, empty projects, and multiple projects in one metrics response.
- [x] 3.2 Add narrow projection records and aggregate mapper queries for grouped version counts, latest task fields ordered by `created_at`/`id`, and total/unresolved counts for latest task IDs.
- [x] 3.3 Implement metrics assembly using the shared accessible-project selector and existing review-state semantics, returning exactly one metric row per accessible summary without loading script content, report JSON/Markdown, issue evidence, excerpts, problems, or suggestions.
- [ ] 3.4 Add SQL-capture/query-bound assertions showing both new reads use a fixed number of statements as project count increases and do not select known long-text columns.
- [ ] 3.5 Run the focused backend tests and confirm metric values, authorization, query bounds, and column boundaries pass.

## 4. Add Query-Supporting Indexes and API Documentation

- [x] 4.1 Add failing `SchemaMigrationTest` assertions for review-project tenant/deletion/update ordering and review-task tenant/project/latest ordering indexes.
- [x] 4.2 Add `V108__optimize_review_library_progressive_reads.sql` with only the two verified composite indexes and update schema expectations so migration tests pass.
- [ ] 4.3 Regenerate the OpenAPI client with `npm run openapi` after the backend exposes the new contracts; do not edit `frontend/src/services/ant-design-pro/` manually.

## 5. Render Summaries Before Metrics in the Library

- [x] 5.1 Run `npx antd info Button`, `npx antd info Badge`, `npx antd info Tag`, and `npx antd info Spin` from `frontend/` before changing their loading, disabled, or retry presentation.
- [x] 5.2 Add failing `script-review-library/index.test.tsx` cases that keep the metrics promise pending and verify rows, name search, import, and neutral navigation actions are usable while metric fields and work-state filters show loading placeholders.
- [ ] 5.3 Add failing library tests for one successful metrics merge, metrics failure with rows preserved, metrics-only retry, import-triggered summary refresh followed by enrichment, and suppression of stale metric responses.
- [x] 5.4 Add typed `queryReviewProjectSummaries` and `queryReviewProjectMetrics` wrappers in the page-owned review service, leaving the legacy wrapper available for unmigrated compatibility callers.
- [x] 5.5 Refactor the library loader to commit summaries first, start one non-blocking metrics request, merge by project ID using a request generation token, and keep list-level and metrics-level loading/error states independent.
- [x] 5.6 Render fixed-size placeholders and metrics-only retry behavior without clearing rows or shifting the list layout, and enable client-side state filters only after current metrics are available.
- [x] 5.7 Run the focused library tests and confirm the initial render occurs before metrics resolution and exactly two page-scoped requests are issued.

## 6. Migrate the Reviewed-Script Picker

- [x] 6.1 Add failing `ScriptContentImport.test.tsx` cases showing project names are selectable from summaries while version counts are pending, then enriched by one batch metrics response.
- [x] 6.2 Update `ScriptContentImport` to use the summary and metrics wrappers, preserve lazy per-project version loading, and retain usable project selection when metrics fail.
- [x] 6.3 Update remaining first-party imports/mocks that still assume the legacy project-list response, then run the picker and affected script-review page tests.

## 7. Verify Performance and Rollout Readiness

- [ ] 7.1 Run backend review/schema tests and the full backend test suite, recording that the new summary and metrics reads pass access and query-bound coverage.
- [ ] 7.2 Run frontend unit tests, `npm run tsc`, `npm run biome:lint`, `npx antd lint ./src`, and the production build; resolve only regressions introduced by this change.
- [ ] 7.3 In a production-like MySQL environment, compare `EXPLAIN ANALYZE`, selected columns, query count, and payload size for legacy, summary, and metrics reads using representative tenants.
- [ ] 7.4 Verify backend-first compatibility, summary-first rendering, metrics failure/retry, legacy endpoint rollback, and separate p50/p95 measurements for time-to-summary and time-to-metrics.
