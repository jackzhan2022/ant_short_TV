## Context

`GET /api/projects/{projectId}/script-workspace` is a legacy aggregate assembled synchronously from the current script, full version contents, episodes, analysis, global understanding, all assets, every asset's visual workspace, and every storyboard. Asset mapping calls the visual-variant, binding, candidate-count, and resolved-image queries once per asset, creating an N+1 query pattern. The script page also uses this aggregate as a five-second analysis poll. The settings, storyboard, video, and shared-header code reuse the same response even when they need only a subset.

The change must preserve tenant/project permission checks, existing editing behavior, and the legacy endpoint while current consumers migrate. It must not modify generated service files directly; frontend request functions remain page-local.

## Goals / Non-Goals

**Goals:**

- Bound each production-workbench page's initial read to the data needed for its first render.
- Eliminate per-asset visual-workspace queries from asset-list loading.
- Load full version bodies, asset visual workspaces, and storyboard details only when the user requests them.
- Replace aggregate polling with the existing lightweight current-analysis contract.
- Keep the legacy aggregate available during migration and cover both old and new contracts with tests.

**Non-Goals:**

- Removing `script-workspace` in this change.
- Changing AI execution, persistence, authorization, or business semantics.
- Introducing a client state-management framework, cache server, or new database schema.
- Redesigning the workbench UI or changing write-operation response contracts.

## Decisions

### 1. Add focused read contracts instead of parameterizing the legacy aggregate

The backend will expose explicit read models:

- `GET /api/projects/{projectId}/script-page-workspace` returns the current script, episodes, episode warnings, analysis snapshot, global-understanding summary, and lightweight version metadata. Version metadata excludes full `content`.
- `GET /api/projects/{projectId}/script-versions/{versionId}` returns one authorized version including its full content when the version viewer is opened.
- `GET /api/projects/{projectId}/asset-settings-summary` returns character, scene, and prop summary fields needed for list/cards, without nested variants, bindings, candidate counts, or resolved-image computation.
- `GET /api/projects/{projectId}/script-elements/{elementType}/{elementId}/visual-workspace` returns the existing nested visual workspace for one authorized asset when its gallery/editor is opened.
- `GET /api/projects/{projectId}/storyboard-workspace?episodeNo={episodeNo}&current={page}&pageSize={size}` returns episode navigation metadata and a page of storyboards for one episode. The server applies bounded defaults and a maximum page size.

These names keep each response purpose explicit and let the legacy endpoint remain unchanged. An `include=` parameter on `script-workspace` was considered, but rejected because it preserves a difficult-to-test combinatorial contract and makes accidental over-fetching easy.

### 2. Use the existing analysis endpoint for polling

The script page will bootstrap from `script-page-workspace`, then poll `GET /api/projects/{projectId}/script-analysis/current` only while a stage is pending, running, or retrying. When polling observes completion, the page performs one script-page refresh to load newly committed episodes and results; terminal failed state updates from the status response without fetching unrelated assets or storyboards.

A new polling endpoint was considered, but the existing current-analysis response already represents the required persisted state and authorization boundary.

### 3. Keep page bootstrap calls independent and resilient

The shared workbench header continues to load project metadata and team points independently. Script, settings, and storyboard child routes call only their focused endpoints. Optional task/result calls remain page-specific and retain their current non-blocking error handling, so a media-task failure does not hide the storyboard list.

### 4. Lazy-load at interaction boundaries

The settings page holds asset summaries as its base state. Opening an asset gallery requests that asset's visual workspace and shows a local loading/error state. After a visual mutation, only the active asset visual workspace and the affected summary item are refreshed.

The storyboard page loads the selected episode's first page. Episode changes cancel/ignore stale responses and load the new episode page. Pagination changes fetch only the requested page. Single-item editing continues to use the current write contracts, followed by a refresh of the active episode page.

### 5. Preserve compatibility and observable performance

The legacy `script-workspace` endpoint and response remain intact. New response DTOs are separate types so serialization changes cannot silently alter legacy clients. Controller tests will assert that summary responses omit heavy fields, authorization is unchanged, pagination is bounded, and detail resources cannot be read across tenant/project boundaries. Service tests will verify that asset summaries do not invoke per-asset visual workspace loading.

Frontend tests will assert endpoint selection, lazy-load timing, stale-response protection, analysis polling, terminal refresh behavior, and partial-error rendering.

## Risks / Trade-offs

- [More HTTP requests after user interaction] → Heavy detail calls occur only when requested and are scoped to one resource; initial page latency and backend work remain bounded.
- [Temporary duplicate contracts] → Keep DTOs and methods clearly named, migrate all known frontend consumers in this change, and document legacy removal as follow-up work.
- [Storyboard UI previously assumed all episodes were resident] → Store episode navigation separately from the active paged result and update mutations to refresh only the active episode.
- [Analysis completion can change page data between polls] → Trigger exactly one script-page refresh on transition to a terminal state and stop the polling timer.
- [Asset cards may need a thumbnail currently produced by expensive resolution] → Include only the persisted `main_image_url` summary field; compute episode-aware resolved media only in the detail endpoint.

## Migration Plan

1. Add focused DTOs, query-service methods, controller endpoints, and backend tests without changing the legacy endpoint.
2. Add frontend types/request functions and migrate the script page, including status-only polling and on-demand version detail.
3. Migrate the settings page to summary loading and per-asset visual-workspace lazy loading.
4. Migrate storyboard and video consumers to episode-scoped storyboard loading while retaining independent task/result requests.
5. Run backend, frontend, lint, and build verification; compare authenticated request duration, query count, and payload size on a large project such as project 33.
6. Deploy backend before frontend. Roll back the frontend independently if needed because the legacy endpoint remains available; roll back the backend only after the frontend rollback.

## Open Questions

None. Endpoint naming, page-size bounds, and exact DTO field lists may follow existing project conventions during implementation without changing these behavioral boundaries.

## Follow-up: Internal Read Boundaries

Authenticated project-33 measurements after the initial deployment showed that response transmission was not the main source of latency. `script-page-workspace`, `asset-settings-summary`, and `storyboard-workspace` spent most of their sampled duration waiting for the server. The storyboard response also still contained every persisted episode body, even when the selected episode had no storyboards.

Focused response DTOs therefore must be paired with focused internal reads. Script and storyboard navigation use an explicit lightweight episode projection that excludes `content`; the existing full-episode read remains only for on-demand episode detail and write/agent flows. Analysis response assembly batches stage-related reads by task/stage IDs rather than querying per stage. Public focused reads resolve tenant membership and project access once, then pass the verified context to internal helpers. A request-scoped, sanitized observer records normalized route name, HTTP duration, SQL count, and cumulative SQL duration without retaining headers, parameters, SQL text, or response content. Database connection wait remains explicitly unavailable until it can be measured independently.
