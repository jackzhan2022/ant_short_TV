## Why

The short-drama creation gallery currently renders original images and videos directly, and its infinite-scroll sentinel can load several pages before the user scrolls. This creates a burst of large object-storage requests and delayed card previews, so the gallery feels slow even when the list API is responsive.

## What Changes

- Generate and store a lightweight thumbnail for every imported inspiration image and video.
- Backfill thumbnails for existing imported inspiration records without re-downloading external source media.
- Return authenticated local thumbnail URLs from the inspiration list and detail APIs while keeping original media URLs for detail playback.
- Render gallery cards from thumbnails and defer non-visible media work.
- Prevent infinite scroll from loading additional pages until the user actually scrolls toward the gallery boundary.
- Allow stable imported media and thumbnails to use browser caching through authenticated platform endpoints.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `public-inspiration-gallery`: Imported inspiration records gain object-storage-backed thumbnails; browse APIs and the short-drama creation gallery use those thumbnails for efficient previews while retaining protected access to original media.

## Impact

- Backend inspiration import, media processing, persistence, response models, and file controllers.
- Database migration for thumbnail metadata and historical backfill state.
- Object storage paths under `inspiration/creations/{externalId}/`.
- Frontend short-drama creation gallery rendering and pagination behavior.
- Image thumbnail generation uses existing Java image APIs; video thumbnail extraction uses the existing in-process JCodec dependency.
