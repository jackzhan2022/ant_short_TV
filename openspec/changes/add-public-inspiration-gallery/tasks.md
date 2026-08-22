## 1. Data Model and Import Foundation

- [ ] 1.1 Add a Flyway migration for `inspiration_creation` with external id uniqueness, browsing metadata, sanitized detail JSON, object storage path, local URL, import state, error tracking, timestamps, and query indexes.
- [ ] 1.2 Add backend entity, mapper, and response DTOs for public inspiration list and detail records.
- [ ] 1.3 Add a media transfer helper that downloads an external image or video once, stores it under `inspiration/creations/{externalId}/original.{ext}`, and records the local URL and MIME type.
- [ ] 1.4 Add an import service that reads the external list API, fetches detail for each item, normalizes author to `管理员`, sanitizes media URLs from detail JSON, and upserts the platform record.
- [ ] 1.5 Add backend tests for migration shape, duplicate external id handling, author normalization, detail sanitization, object storage path derivation, and per-item failure isolation.

## 2. Backend Public APIs

- [ ] 2.1 Implement a public inspiration gallery query service that returns only imported records and supports deterministic ordering.
- [ ] 2.2 Add `GET /api/inspiration-creations` for the lightweight gallery list response without detail JSON.
- [ ] 2.3 Add `GET /api/inspiration-creations/{id}` for a single sanitized detail response.
- [ ] 2.4 Add `GET /api/inspiration-creations/{id}/file` to stream the imported media from object storage.
- [ ] 2.5 Add controller/service tests for list coverage, detail coverage, file streaming, missing-record behavior, and authenticated access behavior.

## 3. Verification

- [ ] 3.1 Run backend tests covering the new import flow, API responses, and file endpoint behavior.
- [ ] 3.2 Run required project checks for impacted backend modules.
- [ ] 3.3 Manually verify the imported records, list API, detail API, and file API behavior using backend fixtures or HTTP requests.
