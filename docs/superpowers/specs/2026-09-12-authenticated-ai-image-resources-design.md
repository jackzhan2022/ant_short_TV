# Authenticated AI Image Resources

## Problem

AI image result URLs are rendered directly by browser `<img>` elements. These native requests include the authenticated session cookie but cannot include the frontend request client's `X-Tenant-Id` header. The current download and thumbnail endpoints require that header in both the permission aspect and controller, so valid images are rejected with HTTP 400 before object storage is read.

## Design

The image download and thumbnail endpoints will resolve the owning tenant from the persisted active image result identified by `projectId` and `resultId`. They will then authorize the current authenticated user against that tenant and project for `AI_IMAGE_TASK:VIEW` before returning any resource.

Only these two read-only resource endpoints will use result-derived tenant resolution. All mutation endpoints and normal JSON APIs will continue requiring `X-Tenant-Id`. Tenant identifiers will not be exposed in URLs, and knowing a result ID will not bypass project membership or permission checks.

The service will return the result metadata and storage resource from one authorized lookup path so the controller does not independently resolve or authorize the same record twice. Missing, deleted, cross-project, or unauthorized results will retain the existing not-found or forbidden behavior.

## Frontend Usage

Gallery tiles will use the result `thumbnailUrl`. The selected large preview and explicit download will use `imageUrl`. Existing persisted `currentImageUrl` values remain valid as original-image URLs; the corresponding selected result supplies the thumbnail URL where available.

## Error Handling

- Unauthenticated requests remain rejected by the existing authentication layer.
- Authenticated users without project view permission receive HTTP 403.
- Unknown, deleted, or project-mismatched results receive HTTP 404.
- Missing objects in MinIO or local storage retain the existing storage error behavior.

## Verification

- Add controller integration coverage proving download and thumbnail requests succeed with an authenticated session and no `X-Tenant-Id` header.
- Preserve coverage proving an authenticated user outside the tenant/project receives HTTP 403.
- Add frontend coverage proving gallery tiles prefer `thumbnailUrl` while the main preview uses the original image URL.
- Run the focused backend and frontend tests, followed by the relevant build/type checks.
