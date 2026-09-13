## 1. Persistence and configuration

- [x] 1.1 Add a Flyway migration for nullable thumbnail path, URL, MIME type, file size, status, error, and the backfill-selection index.
- [x] 1.2 Extend the inspiration entity and configuration properties for thumbnail dimensions, encoding quality, and batch size.
- [x] 1.3 Add schema migration and property-binding tests for the new fields and defaults.

## 2. Thumbnail processing

- [x] 2.1 Write failing tests for bounded image resizing, no-upscale behavior, output MIME metadata, and invalid image handling.
- [x] 2.2 Implement the image thumbnail encoder with WebP support detection and the documented JPEG compatibility fallback.
- [x] 2.3 Write failing tests for video frame extraction failure, cleanup, and successful frame normalization.
- [x] 2.4 Implement the JCodec video frame extractor and ensure all temporary resources are cleaned up.
- [x] 2.5 Add object-storage upload/read support needed by deterministic inspiration thumbnail objects and verify it with service tests.

## 3. Import and historical backfill

- [x] 3.1 Write failing import tests showing image and video thumbnails are uploaded and persisted while thumbnail failures preserve imported originals.
- [x] 3.2 Integrate thumbnail generation into the inspiration import flow with per-item status and error recording.
- [x] 3.3 Write failing backfill tests for bounded selection, ready-row skipping, idempotent deterministic keys, retry, and per-item failure isolation.
- [x] 3.4 Implement the resumable thumbnail backfill service and an authenticated operational entry point consistent with existing administration boundaries.

## 4. Protected API and caching

- [x] 4.1 Write failing controller and response-model tests for `thumbnailUrl`, the authenticated thumbnail endpoint, not-ready handling, and finite private caching.
- [x] 4.2 Extend inspiration list/detail responses and implement thumbnail streaming without exposing object-store or external source URLs.
- [x] 4.3 Replace `no-store` on stable original media with the same finite private cache policy and verify authentication remains enforced.

## 5. Gallery rendering

- [x] 5.1 Write failing frontend tests proving the initial view requests only page one and does not request original media for cards.
- [x] 5.2 Write failing frontend tests for scroll-gated next-page loading, observed thumbnail assignment, and no-thumbnail placeholders.
- [x] 5.3 Add `thumbnailUrl` to the page service model and render a small lazy thumbnail component with asynchronous image decoding and an observer fallback.
- [x] 5.4 Gate the existing infinite-scroll observer on downward user scroll while preserving request deduplication and detail-modal original playback.

## 6. Verification and rollout

- [x] 6.1 Run focused backend inspiration, migration, thumbnail processor, and backfill tests.
- [x] 6.2 Run focused short-drama creation frontend tests, TypeScript checking, Biome lint, and Ant Design lint.
- [x] 6.3 Verify in the browser that the initial gallery contains at most eight cards, card requests target only thumbnail endpoints, and one scroll-boundary event loads one additional page.
- [x] 6.4 Document thumbnail backfill invocation, monitoring, retry, and rollback steps.
