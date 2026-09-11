## Why

Generated image originals and thumbnails are exposed as browser-native image URLs, but their endpoints require the custom `X-Tenant-Id` header that `<img>` requests cannot send. Valid authenticated users therefore receive HTTP 400 and see broken-image alternative text even though both MinIO objects and database records exist.

## What Changes

- Resolve the owning tenant for original and thumbnail resource requests from the active image result identified by `projectId` and `resultId`.
- Authorize the current authenticated user against the resolved tenant and project before streaming either resource.
- Keep all image mutation and JSON endpoints on the existing explicit `X-Tenant-Id` contract.
- Make visual-gallery list tiles use stored thumbnail URLs while large previews and downloads continue using original-image URLs.
- Add regression coverage for headerless authenticated image requests, cross-project/not-found results, and unauthorized users.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `generated-image-storage`: Clarify that browser-native original and thumbnail URLs work with the authenticated session without requiring a custom tenant header, while retaining project-scoped authorization and rendition selection.

## Impact

- Backend image-result controller/service authorization and resource streaming paths.
- Project permission enforcement for the two read-only image resource endpoints.
- Production workbench visual-gallery rendering and its tests.
- No database migration, external dependency, or public object-storage exposure is introduced.
