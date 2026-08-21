# Public Inspiration Gallery Design

## Goal

Build a platform-wide public inspiration gallery from the external creations list and detail APIs. The system imports image and video examples into our object storage, stores only our platform URLs in the database, and exposes separate list and detail APIs for frontend consumption.

## Scope

In scope:

- Import external creation list data and per-creation detail data.
- Download each image or video from the external URL during import.
- Upload imported files to our object storage.
- Store public inspiration metadata in a dedicated platform table.
- Store only our object-storage-backed URL in business data.
- Expose separate list and detail APIs.
- Display every imported creation author as `管理员`.

Out of scope:

- Reusing project-scoped `material` rows for platform gallery data.
- Persisting the external source media URL.
- Tenant- or project-specific permissions for gallery resources.
- Frontend page implementation details beyond API contract shape.

## Data Model

Add a dedicated `inspiration_creation` table for platform public gallery resources.

Required fields:

- `id`: local primary key.
- `external_id`: external creation id, unique.
- `external_task_id`: external task id from the list payload.
- `creation_type`: `image` or `video`.
- `task_type`: external task type.
- `status`: external status value or normalized import display status.
- `sort_order`: source sort value.
- `title`: generated display title, for example `灵感示例-{external_id}` unless detail data provides a better title.
- `author_name`: fixed value `管理员`.
- `url`: our platform file URL only, for example `/api/inspiration-creations/{id}/file`.
- `storage_path`: object storage path, for example `inspiration/creations/{external_id}/original.mp4`.
- `mime_type`: detected or inferred MIME type.
- `file_size`: downloaded file size if available.
- `detail_json`: detail API payload after removing external source media URLs.
- `source_created_at`: creation time from the external list payload.
- `source_updated_at`: update time from the external list payload.
- `import_status`: `PENDING`, `IMPORTED`, or `FAILED`.
- `import_error`: last per-item import error.
- `created_at`, `updated_at`.

Do not store:

- External media URL.
- External author name.
- Bearer token or request headers used for importing.

## Import Flow

The importer takes external API credentials and pagination parameters from configuration or a controlled admin-only request. Credentials are runtime inputs, not committed source code.

Flow:

1. Call the external list API and parse `data[]`.
2. For each creation item, call the external detail API using the external creation id.
3. Read the media URL from list or detail data only in memory.
4. Download the media bytes.
5. Upload bytes to object storage under `inspiration/creations/{externalId}/original.{ext}`.
6. Upsert `inspiration_creation` by `external_id`.
7. Store `url` as our local file endpoint, not the external URL.
8. Store sanitized `detail_json`, with external media URL fields removed or replaced by our local URL where needed.
9. Mark the item `IMPORTED`, or mark `FAILED` with `import_error` while continuing the batch.

Idempotency:

- `external_id` is unique.
- Re-import updates metadata and detail JSON.
- Existing object storage files are not downloaded again by default.
- A later `forceRefresh` option can re-download and overwrite the object file if required.

## API Design

### List API

`GET /api/inspiration-creations`

Purpose: power the gallery grid with lightweight data.

Response item fields:

- `id`
- `creationType`
- `taskType`
- `status`
- `sortOrder`
- `title`
- `authorName`: always `管理员`
- `url`: our platform URL
- `mimeType`
- `sourceCreatedAt`

The list response must not include `detailJson`.

### Detail API

`GET /api/inspiration-creations/{id}`

Purpose: power the detail page or modal.

Response fields:

- All list item fields.
- `detailJson`: sanitized detail payload.
- Any structured detail fields extracted later from `detailJson`.

The detail response must not expose external source media URLs.

### File API

`GET /api/inspiration-creations/{id}/file`

Purpose: stream the imported object from our object storage.

Behavior:

- Loads the local inspiration row.
- Reads `storage_path` from object storage.
- Returns content type from `mime_type` when present.
- Returns 404 if the row does not exist or import did not complete.

## Error Handling

- Invalid external list or detail payload fails the current item, not the whole batch.
- Missing media URL fails the current item with a clear import error.
- Download or object storage upload failure marks the current item `FAILED`.
- Detail API failure may either fail the item or import list metadata only, depending on implementation choice; the recommended default is to fail the item so public data is complete.
- Public list and detail APIs return only `IMPORTED` records by default.

## Testing

Backend tests should cover:

- Parsing the provided list payload.
- Importing image and video items into object storage.
- Persisting only our local URL and storage path.
- Removing or replacing external source media URLs in `detail_json`.
- Displaying author as `管理员` regardless of source author.
- Upserting by `external_id` without duplicate rows.
- Continuing a batch after one failed item.
- Splitting list and detail responses so list omits `detailJson`.
- File API streams the stored object with the expected MIME type.

## Assumptions

- The gallery is platform public and visible to all logged-in users.
- External API credentials are sensitive and must be supplied outside committed code.
- Imported files should remain available even if the external source becomes unavailable.
- The existing `ObjectStorageService` is the storage boundary for uploaded media.
