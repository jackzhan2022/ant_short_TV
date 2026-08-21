## Context

The backend already has a shared `ObjectStorageService`, object-storage-backed material file access patterns, Flyway migrations, and MyBatis-Plus mappers. Current project-scoped media uses the `material` table, while the public style library uses a separate platform table for global browse data. Public inspiration examples are closer to the style library model than project materials because they are platform examples, not tenant-owned production assets.

The external creations API returns a lightweight list of image and video creations and a separate detail endpoint. The imported examples must survive external URL expiry, avoid leaking source-platform media URLs, and present all examples as platform-owned examples with author `管理员`.

## Goals / Non-Goals

**Goals:**

- Add a dedicated platform public inspiration data model.
- Import external list and detail payloads with per-item failure isolation.
- Download source media only during import and upload it into our object bucket.
- Persist only our local file URL and storage path for business reads.
- Return lightweight list data from `GET /api/inspiration-creations`.
- Return sanitized detail data from `GET /api/inspiration-creations/{id}`.
- Stream imported files from `GET /api/inspiration-creations/{id}/file`.

**Non-Goals:**

- Do not store external media URLs after import.
- Do not store or display external author names.
- Do not write public inspiration examples into tenant/project `material` rows.
- Do not add any frontend route, page, or client-side browsing experience in this change.
- Do not add edit, delete, or apply-to-project workflows in this change.
- Do not commit external API credentials or bearer tokens.

## Decisions

### Dedicated `inspiration_creation` table

Use a new platform-scoped table instead of overloading `material`. The table stores stable external identifiers, browsing metadata, import status, sanitized detail JSON, local object storage path, and our local file URL.

Alternatives considered:

- Reuse `material`: rejected because it is tenant/project scoped and would mix public examples with production assets.
- Reuse `style_library`: rejected because that table is image-style-specific and does not fit video creations or per-creation detail data.

### Only persist local media references

The importer reads the external media URL in memory, downloads the file, uploads it to object storage, then persists only `storage_path` and a local API URL such as `/api/inspiration-creations/{id}/file`.

Alternatives considered:

- Persist both original and local URLs: rejected because the user explicitly requested not to store the original URL and because it would leak source-platform details.
- Link directly to the external URL: rejected because external availability and authorization are outside our control.

### Import list and detail separately

The importer mirrors the external API split: list data creates the candidate set, and detail data enriches each record. The public API also uses two read endpoints so the list response remains lightweight and detail consumers load full data only when needed.

Alternatives considered:

- Store and return detail JSON in list results: rejected because it bloats list responses and couples browse responses to detail schema changes.
- Import list metadata only: rejected because detail consumers need stable per-creation context.

### Fixed platform author

Set `author_name` to `管理员` for every imported row and do not persist external author fields. This presents the gallery as a curated platform example space rather than source-user content.

### Sanitized detail JSON

Before persisting detail data, remove external media URL fields or replace detail references with our local URL. Keep enough JSON context for the detail API while ensuring external source media URLs never appear in public responses.

### Controlled import entrypoint

Implement the importer as a backend service that can be called by an admin-only endpoint or command-style internal runner. External credentials and tenant headers are runtime configuration or request input, not committed code. The first implementation can prioritize an admin endpoint if that matches existing auth patterns.

## Risks / Trade-offs

- External detail payload shape may change → Store raw sanitized JSON defensively and keep structured columns limited to stable list fields.
- Large video downloads can consume memory or time → Stream downloads where practical, enforce reasonable timeouts, and record per-item failures without stopping the batch.
- Object storage upload may partially succeed while database write fails → Use deterministic storage paths and idempotent upsert so re-import can recover.
- Sanitization may miss a nested external URL → Add recursive JSON sanitization tests using list and detail fixtures.
- Public file endpoint might expose failed imports → Public query and file APIs must only serve `IMPORTED` rows.
- Existing worktree has multiple unrelated changes → Keep implementation scoped to the new inspiration module, backend endpoints, tests, and migration.

## Migration Plan

1. Add a Flyway migration for `inspiration_creation` with a unique index on `external_id` and query indexes for import status, type, and ordering.
2. Deploy backend import/query/file APIs.
3. Configure object storage and external import credentials outside source control.
4. Run the import in a controlled environment and verify imported rows use only local URLs.
5. Deploy the backend APIs and internal import path.

Rollback:

- Stop invoking the importer.
- Leave imported object files and table rows in place unless a cleanup migration or manual operational cleanup is explicitly requested.

## Open Questions

- The exact external list pagination parameters should be confirmed during implementation from the captured browser request or live API response.
- The exact admin import trigger can be chosen during implementation based on existing security conventions: admin-only REST endpoint or internal runner.
