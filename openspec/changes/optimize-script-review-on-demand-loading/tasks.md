## 1. Define page-scoped contracts and test fixtures

- [x] 1.1 Add or update backend response models for lightweight project summaries, paged project review-history rows, and selected-task detail so each contract excludes data owned by the next navigation level.
- [ ] 1.2 Add controller/service contract tests covering response field boundaries, newest-first stable history paging, every persisted review status, and legacy route identifier preservation.
- [ ] 1.3 Add authorization tests for creator-owned unbound drafts, accessible bound projects, inaccessible projects, and cross-tenant project/task identifiers across all three read endpoints.

## 2. Implement bounded backend reads

- [x] 2.1 Replace library summary mapping-time lookups with fixed batch queries for accessible projects, latest versions, latest tasks, and outstanding issue aggregates while preserving current display-state semantics.
- [x] 2.2 Implement the paged `GET /api/script-review/projects/{projectId}/reviews` read with project header data, content-free version metadata, lightweight task rows, aggregate issue counts, and deterministic creation-time/ID ordering.
- [x] 2.3 Refactor `GET /api/script-review/tasks/{taskId}` to load only the selected task and bound version body, batch-load its issues and hits, and batch-load fanout cache usage for relevant run IDs.
- [ ] 2.4 Add persistence/integration tests with multiple projects, tasks, issues, hits, and fanout units that verify query counts stay bounded as seeded record counts increase.

## 3. Update frontend data access and routes

- [x] 3.1 Regenerate or update the frontend API client from the backend contract so the three page-scoped read models and history pagination are typed without editing generated service files by hand.
- [x] 3.2 Add routes for `/script-review/projects/:projectId/reviews` and `/script-review/tasks/:taskId`, plus compatibility redirects from supported legacy `/script-review` project/task links.
- [x] 3.3 Change the existing script-review library to render its unchanged layout, search, filters, counts, and next-step actions from the single lightweight project-list response with no per-project detail requests.
- [x] 3.4 Preserve the import modal flow, refresh only the lightweight library after success, and navigate to the newly created project's history route.

## 4. Build project review history

- [x] 4.1 Implement the dedicated project history page with project header, version metadata needed for review creation, status filtering, empty/error/retry states, and paged review-attempt rows.
- [x] 4.2 Render every attempt independently with task/round identity, version and file, mode, scope, dimensions, status, progress, issue/unresolved counts, creator, timestamps, and concise failure information.
- [x] 4.3 Wire history-row actions so active attempts expose progress/cancellation as allowed, failed attempts expose retry as allowed, completed attempts open the dedicated task-detail route, and starting a review follows the created task.

## 5. Focus the review-detail workbench

- [x] 5.1 Move the existing review workbench presentation to the task-detail route and bind it only to the selected task response, selected version content, issues, hits, progress, fanout, and observability data.
- [x] 5.2 Preserve issue selection, manual resolution, batch repair, retry, cancellation, export, responsive layout, and action-specific refresh behavior without fetching project-wide task or version bodies.
- [x] 5.3 Add level-scoped loading and failure handling so task-detail errors remain on the requested task and never fall back to another project or task.

## 6. Verify behavior and rollout readiness

- [ ] 6.1 Add frontend request-shape tests proving library entry makes no project-detail calls, history entry makes no task-detail calls, and task-detail entry requests only the selected task.
- [ ] 6.2 Add frontend interaction tests for library filters/import navigation, history status rows and pagination, legacy redirects, detail actions, and constrained-view workbench access.
- [ ] 6.3 Run backend tests, frontend unit/type/lint checks, and production builds; compare representative library/history/detail request counts and payloads with the recorded baseline.
- [ ] 6.4 Verify backend-first deployment compatibility, permission-scoped reads, legacy-link redirects, and rollback behavior in a production-like environment before release.
