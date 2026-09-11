## Why

Opening the script review library currently waits for version, task, issue, and per-project authorization work before the first useful list can render. Production observation shows `GET /api/script-review/projects` taking about four seconds even though its response is only a few kilobytes, so the read contract must prioritize the immediately visible project rows and defer secondary metrics.

## What Changes

- Make the project-list endpoint return only permission-scoped, render-critical summary fields needed to paint and operate the library immediately.
- Add a batch metrics endpoint that returns version count, latest review round, and outstanding issue count for the project identifiers visible in the loaded list.
- Load the metrics once after the initial list renders and merge them into existing rows without clearing the list or blocking search, filters, navigation, or import actions.
- Resolve the accessible review-project set in bulk and replace full-entity version/task/issue reads with projections or aggregate queries that do not load script content, report JSON, evidence, or other long-text fields.
- Add query-bound and request-shape coverage so the initial list remains independent of project count and secondary metrics remain a single batch request.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `script-review-library`: Split library discovery into a render-critical project summary followed by non-blocking batch metrics while preserving permission scoping, client-side discovery, navigation, and import behavior.

## Impact

- Backend review controller, service, mapper projections/aggregate queries, response models, authorization batching, and relevant database indexes.
- Frontend script-review service types and the script-review library loading/merging states.
- API documentation and generated client definitions after the backend contract changes; generated service files remain generator-owned.
- Backend integration tests and frontend request-shape/rendering tests.
