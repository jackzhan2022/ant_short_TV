# Verification record

## Request and payload comparison

| Entry point | Recorded baseline | Current request shape | Payload boundary |
| --- | --- | --- | --- |
| Library | 6 review requests for 5 visible projects | 1 `GET /api/script-review/projects` | Summary fields only; no version bodies, issues, hits, fanout, or observability |
| Project history | Project detail was part of the shared workbench load | 1 `GET /api/script-review/projects/{projectId}/reviews?page=1` | Project header, version metadata, and lightweight task rows only |
| Task detail | Approximately 95 KB project detail plus 183 KB version history before detail settled | 1 `GET /api/script-review/tasks/{taskId}` | Selected task and its bound version only; no sibling tasks or version bodies |

Frontend request-shape tests enforce the one-request entry behavior. Backend controller tests enforce the response-field boundaries and verify that mapper query counts do not grow when additional projects, tasks, issues, hits, and fanout units are seeded.

## Local verification

- Frontend focused review tests: 4 files, 23 tests passed.
- Frontend full unit suite: 65 files, 249 tests passed.
- Frontend Biome lint and TypeScript checks: passed; six pre-existing warnings remain outside this change.
- Frontend production build: passed.
- Backend review controller integration suite: 13 tests passed, including paging, authorization, field-boundary, and bounded-query scenarios.
- The repository-wide backend suite was sampled without failures, then stopped because its configured one-JVM-per-class isolation would require roughly an hour; the complete change-owned controller suite above is the release evidence for this change.
- Backend production package: passed; the JAR contains `BOOT-INF/classes/` and is executable by Spring Boot.

## Production deployment evidence (2026-09-07)

- Release `202609072255-flyway` deployed after enabling Flyway out-of-order compatibility for previously skipped migrations `V95/V96`.
- `antv.service` is `active`; `/opt/antv/current` resolves to the new release.
- Smoke checks: `http://127.0.0.1:8080/api/currentUser` = `401`, homepage = `200`, `/api/auth/bootstrap` = `401`.
- Previous release `202609071647-457bee5` was retained and used for rollback during the deployment attempt; it restored healthy service checks before the corrected release was promoted.

## Release verification boundary

The repository deployment runbook requires a clean pushed commit, SSH access to the internal-test host, versioned backend/frontend artifacts, an existing release retained for rollback, and authenticated post-deploy smoke checks. Those external deployment steps are not performed by local tests. Before release, deploy the additive backend reads first, exercise creator/project-member/cross-tenant reads, then deploy the frontend. Rollback must switch the frontend/application release to the previous version first; no schema rollback is required for this change.
