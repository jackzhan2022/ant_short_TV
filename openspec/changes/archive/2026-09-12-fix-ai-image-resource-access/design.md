## Context

Completed AI image results persist an original object and a PNG thumbnail in object storage. Their API URLs are assigned directly to browser `<img>` elements. Authentication is cookie based, but selected-tenant context is normally supplied by the frontend request client through `X-Tenant-Id`. Native image requests cannot add that header, so the project-permission aspect rejects both resource endpoints before the controller can stream the stored object.

The result row already contains globally unique result identity, `project_id`, and `tenant_id`. That ownership data is authoritative for resolving the authorization scope of a read-only resource request.

## Goals / Non-Goals

**Goals:**

- Allow authenticated browser-native requests to load generated originals and thumbnails without a custom tenant header.
- Preserve tenant isolation, project membership checks, and `AI_IMAGE_TASK:VIEW` enforcement.
- Use thumbnails for gallery tiles and originals for large preview/download.
- Preserve existing URLs and stored records.

**Non-Goals:**

- Making object-storage paths or buckets public.
- Changing tenant resolution for mutations or JSON APIs.
- Introducing signed MinIO URLs, a CDN, or a database migration.
- Backfilling results that predate thumbnail storage.

## Decisions

### Resolve authorization scope from the requested result

The two resource endpoints will bypass the header-dependent permission aspect and delegate to a service operation that loads an active result by ID, verifies its `project_id` matches the path, derives its `tenant_id`, and invokes the existing project permission guard for `AI_IMAGE_TASK:VIEW`. Only after authorization succeeds will the service access object storage.

This keeps the tenant ID out of the URL and works with the existing authenticated session. A query-string tenant ID was rejected because it duplicates authoritative ownership data and spreads tenant selection into stored URLs. Fetching every image as a frontend Blob was rejected because it complicates caching, cancellation, and object-URL cleanup.

### Return metadata and resource from one authorized service call

The service will expose one result-resource operation per rendition, returning the result metadata needed for content type together with the resource. This avoids repeated database lookups and prevents the controller from authorizing metadata and content through separate paths.

### Keep explicit tenant context everywhere else

Only GET requests for generated image original and thumbnail resources use ownership-derived tenant resolution. Create, regenerate, select, save, delete, and task-list/detail operations retain their current `X-Tenant-Id` requirement.

### Select renditions at the UI boundary

The visual-gallery data model will expose the selected result's thumbnail URL. Gallery tiles prefer that thumbnail, with the original URL only as a compatibility fallback. Large preview and download continue using the original URL.

## Risks / Trade-offs

- [Resource lookup occurs before authorization] -> Restrict the initial lookup to active results and require an exact project match; return not found without exposing object metadata, then enforce existing project permission before storage access.
- [Removing the annotation could accidentally remove authorization] -> Put permission enforcement inside the resource service method and add tests for an authenticated user without project access.
- [Older visual variants may not have a resolvable thumbnail] -> Preserve original URL fallback for tiles while all newly completed results provide both renditions.
- [Browser/proxy caching could retain a previous response] -> Keep authenticated same-origin URLs and existing cache behavior; this change does not make responses public.

## Migration Plan

Deploy backend and frontend together. No data migration is required because existing result rows already contain tenant, project, original path, and thumbnail path. Rollback restores the previous application release; stored data remains compatible in both directions.

## Open Questions

None.
