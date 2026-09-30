## 1. Tencent Cloud Environment Readiness

- [ ] 1.1 Configure both pre-release production and deployed test profiles to use private bucket `antv-1418200553` and CDN domain `antvcdn.aixmax.cn`, and record the post-launch follow-up to separate test resources.
- [ ] 1.2 Attach least-privilege CAM instance-role policies for backend COS object, multipart, metadata, STS delegation, and Cloud Infinite operations in production and test.
- [ ] 1.3 Configure intelligent tiering, three-day incomplete-multipart cleanup, seven-day unconfirmed-upload cleanup, 14-day failed-intermediate cleanup, CORS allowlists, disabled versioning, and disabled cross-region/global acceleration.
- [ ] 1.4 Verify `antvcdn.aixmax.cn` CNAME, HTTPS, private-origin authorization, Type D authentication, 30-day node caching, seven-day browser caching, `sign,t` cache-key exclusion, processing-parameter retention, disabled auto-refresh, and coalesced origin requests.
- [ ] 1.5 Configure video-only range origin rules and prove a private test video returns valid `206 Partial Content` responses without full-object origin transfer.
- [ ] 1.6 Configure Cloud Infinite original-only image and video workflows, persistent `derived/` outputs, authenticated callback routing, and recursion exclusions.
- [ ] 1.7 Create COS, CDN, Cloud Infinite, retrieval, request-count, storage, and application public-bandwidth budgets and alert thresholds.

## 2. Dependencies, Configuration, And Schema

- [x] 2.1 Add a current pinned `com.qcloud:cos_api` dependency and backend configuration for region, bucket, storage class, CDN domain, Type D secrets, STS duration, and rendition settings.
- [x] 2.2 Add `cos-js-sdk-v5` to the frontend with lockfile updates and isolate it behind a project upload client.
- [x] 2.3 Add Flyway migrations for upload sessions, immutable object/rendition metadata, Cloud Infinite processing jobs, and seven-day media delivery grants with uniqueness and concurrency constraints.
- [x] 2.4 Add production/test configuration validation that rejects blank COS settings, permanent browser credentials, or missing CDN signing material while permitting the documented pre-release shared bucket and CDN.
- [x] 2.5 Update `env.example` and deployment documentation with secret placeholders and CAM-role requirements without including real keys or signed URLs.

## 3. COS-Only Storage Core

- [x] 3.1 Replace the MinIO implementation behind `ObjectStorageService` with one lifecycle-managed COS client using the CAM instance-role credential provider and `ap-guangzhou` regional access.
- [x] 3.2 Implement normalized immutable key construction for project assets, tenant-level decomposition uploads, originals, fixed derivatives, covers, and temporary upload sessions.
- [x] 3.3 Implement streaming and multipart backend uploads, object reads, `HEAD` metadata verification, idempotent delete, and object-scoped presigned URLs without buffering full videos.
- [ ] 3.4 Persist verified MIME type, size, ETag/checksum, dimensions, storage class, original key, and rendition keys while preventing durable COS/CDN/provider URL storage.
- [ ] 3.5 Add redacted COS diagnostics and metrics for request count, latency, retries, transfer bytes, failures, and internal-route verification.
- [x] 3.6 Convert storage unit and slice tests to mocks/fakes so normal automated tests never contact a real bucket.

## 4. Browser Multipart Uploads And STS

- [x] 4.1 Implement authenticated upload-session create, 60-minute STS renew, completion, cancellation, and status APIs for project and tenant-level media.
- [x] 4.2 Generate session-specific CAM policies that allow only required multipart actions under the assigned upload prefix and reject cross-tenant/project keys.
- [x] 4.3 Verify completed uploads with `HEAD Object`, reject inconsistent key/size/ETag content type evidence, and publish no business record before verification succeeds.
- [x] 4.4 Implement a frontend COS multipart client with progress, pause/resume where supported, automatic STS renewal, offline recovery, normalized errors, and no application file-size ceiling.
- [x] 4.5 Replace video-decomposition Spring byte uploads with tenant/project upload sessions and verified completion metadata.
- [x] 4.6 Replace inspiration-management browser byte uploads with scoped COS upload sessions while preserving form metadata and retry behavior.
- [x] 4.7 Regenerate generated frontend services with `npm run openapi` after upload API contracts stabilize; do not hand-edit `frontend/src/services/ant-design-pro/`.

## 5. Private CDN Delivery

- [x] 5.1 Implement Type D URL signing for `antvcdn.aixmax.cn` with secret redaction and deterministic output from an immutable object key and persisted expiry.
- [x] 5.2 Implement transactional `media_delivery_grant` creation and renewal per caller/resource/version/rendition so the complete URL remains stable for seven days.
- [x] 5.3 Renew video grants before playback when less than two hours remain and keep ordinary image/audio grants fixed until their stored period expires.
- [ ] 5.4 Replace stored/public URL resolution with permission-checked original, display, thumbnail, cover, audio, and video delivery URL resolution while preserving existing resource authorization boundaries.
- [x] 5.5 Implement task-bounded COS presigned URLs for external AI access separately from browser CDN grants.
- [x] 5.6 Add log and exception redaction tests covering Type D secrets, signed query strings, STS values, COS authorization headers, and model-access URLs.

## 6. Persistent Cloud Infinite Renditions

- [ ] 6.1 Implement idempotent Cloud Infinite job submission and persistence for one intelligent-compression display WebP per image and deterministic video cover WebP outputs.
- [x] 6.2 Implement authenticated, idempotent callback processing that validates job/input/output correlation and records terminal rendition metadata or retryable failure.
- [x] 6.3 Route every new generated, uploaded, imported, reference, style, and cover image original through the fixed persistent WebP rendition workflow.
- [x] 6.4 Update generated-image completion to publish only after its required compressed display WebP object is ready.
- [x] 6.5 Implement deterministic video cover selection: AI first-frame reuse, episode first-storyboard-frame reuse, and one-second Cloud Infinite snapshot with short-video fallback.
- [x] 6.6 Honor `generateCover=false` for optional episode covers while retaining required-thumbnail behavior in domains that mandate a cover.
- [x] 6.7 Remove JCodec video thumbnail extraction, synthetic episode-cover generation, and Java image-thumbnail generation from migrated flows after equivalent Cloud Infinite tests pass.
- [x] 6.8 Prevent arbitrary client rendition parameters, recursive `derived/` processing, unconditional intelligent-cover analysis, and unconditional video transcoding.

## 7. Domain Integration And MinIO Removal

- [ ] 7.1 Migrate AI image originals/results, visual variants, and saved materials to COS keys, Cloud Infinite renditions, and CDN delivery grants.
- [x] 7.2 Migrate AI video provider downloads to streaming COS persistence and reuse their bound first-frame cover sources.
- [x] 7.3 Migrate shot/episode video, audio, subtitle, cover, and material flows to immutable COS keys without local-file fallbacks.
- [x] 7.4 Migrate public inspiration import and management media to streaming COS originals, persistent Cloud Infinite renditions, and authorized CDN responses.
- [x] 7.5 Migrate style-library media and any remaining `ObjectStorageService` callers, proving no code path still branches on MinIO/local enablement.
- [x] 7.6 Remove MinIO dependency, properties, bucket auto-creation, `enabled()` branches, local runtime resource handling, and obsolete environment documentation.
- [x] 7.7 Add an explicit not-found/legacy-unavailable outcome for historical MinIO-only object keys without attempting fallback or backfill.

## 8. Verification And Release

- [x] 8.1 Add backend tests for key ownership, CAM/STS policy scope, upload completion verification, grant concurrency, Type D signing, expiry/renewal, model URLs, and callback idempotency.
- [x] 8.2 Add frontend tests for multipart progress, credential renewal, offline/COS/completion errors, resume behavior, and use of thumbnail/display/video delivery URLs.
- [ ] 8.3 Run backend tests and build, frontend tests, `npm run lint`, `npx antd lint ./src`, and the repository's required type/build checks.
- [ ] 8.4 Run pre-release integration checks against the shared empty production bucket for backend internal upload, browser direct upload, object metadata, intelligent tiering, lifecycle targeting, Cloud Infinite outputs, and CDN delivery.
- [ ] 8.5 Verify correct, expired, and tampered Type D URLs; unauthorized resource requests; seven-day stable URL reuse; browser cache behavior; and CDN HIT reuse across changed signatures.
- [ ] 8.6 Verify video range seeking, no full-object origin transfer for partial playback, no application-server media proxying, and acceptable public-bandwidth usage under concurrent uploads and playback.
- [ ] 8.7 Verify cost telemetry for COS requests/storage/retrieval, CDN origin/downstream transfer, Cloud Infinite processing, abandoned uploads, and alert delivery.
- [ ] 8.8 Perform a release and rollback drill documenting that rollback restores MinIO-era behavior for old data while COS-only objects created after cutover remain unavailable to the old release.
- [ ] 8.9 Record production readiness evidence, known breaking behavior, the post-launch test bucket/CDN separation follow-up, and the separate follow-up required for historical MinIO migration.
