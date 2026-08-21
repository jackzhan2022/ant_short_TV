## Why

The platform needs a public example inspiration gallery so users can browse proven image and video outputs before creating their own short drama assets. The provided external creations API already contains suitable examples, but relying on remote media URLs would make the gallery fragile and expose source-platform implementation details.

## What Changes

- Add a platform-wide public inspiration creation library for image and video examples.
- Import external creation list records and per-creation detail records.
- Download each creation media file during import and store it in the platform object bucket.
- Persist only our platform file URL and object storage path, never the external media URL.
- Normalize every displayed author to `管理员`.
- Expose separate backend APIs for gallery lists and individual creation details.
- Expose a file streaming API backed by our object storage.
- Keep inspiration gallery data separate from tenant/project-scoped `material` records.

## Capabilities

### New Capabilities

- `public-inspiration-gallery`: Platform public image/video inspiration records, object-bucket media transfer, sanitized metadata, list/detail/file APIs, and gallery browsing behavior.

### Modified Capabilities

- None.

## Impact

- Backend: new Flyway migration, entity/mapper, import service, query service, controller endpoints, and object storage integration.
- Frontend: new service helpers, route/menu entry, public inspiration gallery list page, and detail view/modal.
- Tests: backend import/query/file API coverage and frontend route/page/service tests.
- Operations: external API credentials must be supplied through runtime configuration or controlled admin-only import input, not committed source code.
