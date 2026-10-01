## Why

The application currently routes durable media through a MinIO-specific service and often proxies media bytes through a 5 Mbps application server. Moving new media to Tencent COS, private CDN delivery, and selective Cloud Infinite processing removes that bandwidth bottleneck, reduces repeated origin traffic, and establishes one cloud-native storage contract for generated and uploaded assets.

## What Changes

- Replace MinIO-backed production storage with private Tencent COS storage in Guangzhou, using CAM-associated instance credentials for backend access and the official COS Java SDK.
- Add browser-to-COS multipart uploads using short-lived, server-generated request signatures scoped to an authenticated upload session so external uploads do not traverse the application server; the application will not impose a business file-size ceiling.
- Persist tenant/project/asset/version-scoped immutable object keys and keep only object keys and rendition metadata in business records.
- Deliver authorized private media through `antvcdn.aixmax.cn` with Type D signed URLs, persisted seven-day authorization periods, seven-day browser caching, and 30-day CDN node caching.
- Configure CDN cache-key behavior to exclude Type D `sign` and `t` parameters while preserving media-processing parameters, and enable coalesced origin requests plus video-only range origin requests.
- Generate one persistent Tencent Cloud Infinite `imageSlim` display derivative for every stored image while retaining the original object. Sources unsupported by `imageSlim` are converted to PNG before compression.
- Reuse an AI video's bound first-frame image as its cover; use persistent Cloud Infinite snapshots for videos without a suitable bound first frame and remove server-side JCodec cover extraction from new flows.
- Use COS intelligent tiering, lifecycle cleanup for incomplete multipart uploads and abandoned temporary objects, and cost/usage observability.
- Keep content moderation out of the first phase and perform Cloud Infinite video processing only when a durable cover or compatibility rendition is required.
- **BREAKING** Stop reading and writing MinIO when the COS implementation is enabled. Existing MinIO-only media will remain unavailable until a separate historical migration is designed and executed.
- **BREAKING** Replace authenticated application streaming URLs for newly stored media with permission-checked, time-bounded CDN or model-access URLs; callers must not treat persisted URL strings as durable resource identities.

## Capabilities

### New Capabilities
- `tencent-cos-media-storage`: COS object-key ownership, backend internal-network access, browser multipart upload sessions, request-signing scope, lifecycle cleanup, and MinIO removal.
- `private-media-delivery`: Project-authorized Type D CDN delivery, persisted authorization periods, cache-key rules, browser/node caching, and model-access URLs.
- `persistent-media-renditions`: Cloud Infinite persistent `imageSlim` display derivatives and deterministic video-cover selection and snapshot processing.

### Modified Capabilities
- `generated-image-storage`: Replace server-generated thumbnails and application streaming with persistent Cloud Infinite `imageSlim` renditions and authorized CDN delivery while preserving originals for download.
- `inspiration-gallery-management`: Replace frontend size-constrained image upload and server-side video-frame extraction with COS upload sessions and persistent Cloud Infinite renditions.
- `public-inspiration-gallery`: Store and serve imported-media renditions from COS/CDN instead of local protected streaming endpoints and server-generated thumbnails.
- `video-script-decomposition`: Move browser video upload bytes off the application server while preserving tenant ownership validation and controlled model-access URLs.

## Impact

- Backend: storage, material access, AI image/video persistence, shot composition, inspiration import/management, video decomposition, authorization, configuration, database migrations, and operational diagnostics.
- Frontend: upload session orchestration, COS multipart progress/retry, and consumption of authorized rendition URLs.
- Dependencies: add `com.qcloud:cos_api` and `cos-js-sdk-v5`; use the COS SDK signer with the attached CVM role; remove the MinIO client after all new code paths have moved.
- Infrastructure: the owner-approved pre-release production deployment also serves real-cloud integration testing, using bucket `antv-1418200553` in `ap-guangzhou` and CDN domain `antvcdn.aixmax.cn`. No second test deployment is required before public launch; any additional deployed test environment must use the same private storage/delivery contract. The bucket remains private read/write with intelligent tiering, CAM policies, Type D keys, HTTPS, CORS, lifecycle rules, Cloud Infinite workflows, and cost alerts. Separating test resources before tests could affect live customers after launch is an operational follow-up.
- Data: new authorization-period and rendition-processing state; no historical MinIO object or database backfill in this change.
