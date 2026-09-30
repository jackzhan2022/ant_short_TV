## Context

The backend currently exposes a concrete `ObjectStorageService` implemented with the MinIO Java client. Callers branch on `enabled()` and fall back to local files, browser uploads traverse Spring MVC, material URLs mix storage identity with expiring access URLs, and several media paths generate thumbnails or covers in-process. This creates a 5 Mbps public-bandwidth bottleneck, stores provider-specific URLs in business records, and prevents the application from using COS private origin delivery and Cloud Infinite consistently.

The pre-release production and test deployments both target private bucket `antv-1418200553` in `ap-guangzhou`, accessed by the backend over Tencent Cloud's same-region internal network with a CAM-associated instance role. Browser delivery uses `antvcdn.aixmax.cn`, HTTPS, private-origin authorization, and Type D URL authentication. The bucket uses intelligent tiering. This shared configuration is intentional while the product has not launched publicly; the test environment will move to a separate bucket and CDN through a later configuration-only operational change.

Existing MinIO objects will not be copied or read by the new implementation. The owner accepts that those historical assets remain unavailable until a later migration. Content moderation is also deferred.

## Goals / Non-Goals

**Goals:**

- Make COS the only runtime object store for all new backend-generated and browser-uploaded media.
- Keep media bytes off the application server's public 5 Mbps link wherever the producer can upload directly.
- Keep buckets private and issue only authorization-scoped, time-bounded upload request signatures or delivery grants.
- Persist immutable object keys and rendition state instead of provider URLs.
- Maximize CDN/browser cache reuse without allowing Type D authentication parameters to fragment the CDN cache.
- Persist one original-resolution `imageSlim` display derivative for every image and deterministic compressed covers for videos through Cloud Infinite.
- Preserve tenant/project authorization, asynchronous task reliability, and actionable processing failures.

**Non-Goals:**

- Copying, backfilling, or serving historical MinIO objects.
- Dual-writing to COS and MinIO or falling back to MinIO after a COS failure.
- Content moderation, intelligent-cover selection, automatic multi-bitrate generation, or unconditional video transcoding.
- Separating the test bucket and test CDN before post-launch testing against live production data.
- Imposing an application-level maximum upload size; COS platform limits still apply.

## Decisions

### Keep the storage facade and replace its implementation with COS only

Business services will continue to depend on one storage facade, but its contract will expose object operations and metadata rather than MinIO state. The COS implementation will use `com.qcloud:cos_api`, one lifecycle-managed thread-safe client, the configured region and bucket, intelligent-tiering writes, and the CAM instance-role credential provider. Leading slashes will be removed from all new keys.

`enabled()`, MinIO bucket auto-creation, and local-file runtime fallbacks will be removed. Unit tests will use mocks or fakes; deployed test environments will use their own COS bucket. Directly replacing call sites with `COSClient` was rejected because it would duplicate key validation, metadata, retry, credential, and observability behavior across domains.

### Use immutable tenant/project/asset/version object keys

New durable keys will follow this shape:

```text
materials/{tenantId}/{projectId}/{assetType}/{yyyyMM}/{assetId}/{versionId}/original.ext
materials/{tenantId}/{projectId}/{assetType}/{yyyyMM}/{assetId}/{versionId}/derived/display.{jpg|png|gif}
materials/{tenantId}/{projectId}/{assetType}/{yyyyMM}/{assetId}/{versionId}/derived/cover.{jpg|png}
uploads/{tenantId}/{projectId}/{uploadSessionId}/source.ext
```

Unbound tenant-level decomposition uploads will use a tenant-scoped equivalent without fabricating a project ID. Business rows will store object keys, MIME type, length, ETag/checksum, dimensions, and rendition status. COS/CDN hostnames and signed query strings will not be durable identities. Overwriting an existing immutable key is rejected; new content creates a new version ID and key.

### Separate backend internal uploads from browser external uploads

Backend-generated media will stream to COS over same-region internal networking. Provider result downloads will be piped to multipart COS upload instead of materializing an entire video as `byte[]`.

Browser uploads will use `cos-js-sdk-v5` and a server-created upload session. For each COS request, the browser sends the SDK's method, pathname, query, and signable headers to the authenticated upload-session authorization endpoint. The backend verifies session ownership and state, requires the exact assigned object pathname, allows only the methods, multipart query keys, and headers needed by direct upload, and signs the request for a short interval with its CAM instance-role credentials. The response contains only the request authorization and the instance credential's security token; it never returns a SecretId or SecretKey. Long-running uploads obtain fresh per-request signatures without changing the upload session. There is no application file-size ceiling, but the server still validates ownership, supported media type, key prefix, declared metadata, and platform limits. Completion is accepted only after the backend performs `HEAD Object` and validates the actual key, length, ETag, and content type. Abandoned multipart uploads expire after three days and unconfirmed staging objects after seven days.

Anonymous/public writes, permanent browser credentials, and returning the CVM role's temporary SecretId or SecretKey to the browser were rejected. A second assumable role was also rejected because the selected single-role CVM trust policy is service-managed. Keeping browser bytes behind Spring was rejected because it would saturate the 5 Mbps public link.

### Persist authorization periods and derive stable Type D URLs

Before returning a private browser URL, the backend will enforce the current tenant/project/resource permission. A `media_delivery_grant` row will identify the subject user, resource/version, rendition/object-key hash, authorization revision, and `expires_at`. The full URL and Type D secret will not be stored.

The backend will derive the same Type D URL from the stored seven-day period throughout that period. Concurrent renewal will use a unique key plus compare-and-set/transactional update so only one next period wins. Video playback will renew before presentation when less than two hours remain. Revocation through ordinary permission changes prevents new grants; already issued URLs and browser-cached bytes remain usable until expiration. Emergency revocation requires object-key replacement or object removal plus targeted CDN purge.

CDN nodes cache immutable objects for 30 days and browsers for seven days. CDN cache keys exclude only Type D `sign` and `t`; Cloud Infinite and other representation parameters remain significant. Coalesced origin requests stay enabled. Range origin requests are enabled only for durable video extensions, and deployment verification must prove `206 Partial Content` seeking behavior.

### Use separate URLs for browser delivery and external model access

Browser URLs use the private CDN contract. External AI providers receive a COS presigned URL produced only after the same resource authorization and scoped to the task timeout plus 30 minutes. Model URLs are not persisted in business URL fields or reused as browser URLs. This avoids coupling long-running provider access to the seven-day browser grant and CDN cache configuration.

### Persist fixed Cloud Infinite image renditions

Every accepted image original, including generated images, reference frames, style images, and video covers, will trigger one idempotent Cloud Infinite processing job that preserves the original dimensions and writes one fixed `imageSlim` display rendition. JPEG, PNG, and GIF sources are compressed directly without format conversion. Sources not accepted by `imageSlim`, including WebP, are converted to PNG first so transparency is retained and are then compressed once. List thumbnails, detail views, and normal previews resolve only to this immutable display object; the original is available only through an explicit download action. A failed display rendition never falls back to showing the original.

Processing inputs are restricted to original-key patterns and outputs use `derived/`, preventing recursive workflow triggers. Callback handling verifies the Tencent signature, correlates the job to one asset version, and is idempotent. A required rendition failure leaves that rendition failed/retryable and prevents domain flows that require it from publishing a falsely complete result.

Persistent derivatives were selected over unrestricted real-time query processing because each fixed variant is processed once, has a stable cacheable key, and avoids repeated processing charges or unbounded parameter combinations. Resizing and destructively replacing originals were rejected because the requested display derivative must retain source dimensions and downloads, editing, and later reprocessing require source fidelity.

Async image jobs use `CreateMediaJobs` with `Tag=PicProcess`; Tencent selects the active picture-processing queue and returns its `QueueId`, so the application does not persist a configured queue ID. Each submission provides a task-specific JSON callback URL. Tencent's callback contract does not define a callback signature header, so callback authentication uses a per-job high-entropy bearer token whose hash is persisted with the job. Callback acceptance also requires an exact match on provider job ID, input object, output object, and opaque `UserData`; the token is single-purpose and terminal callbacks remain idempotent.

### Derive video covers deterministically

AI-generated video reuses its bound first-frame image and persists an `imageSlim` cover rendition through Cloud Infinite. Episode compositions prefer the first storyboard frame. Uploaded, imported, or otherwise coverless videos use a persistent Cloud Infinite snapshot at one second, with a bounded fallback to the first decodable frame when the video is shorter, followed by `imageSlim`. New flows will not use JCodec extraction or the synthetic `episodeFrame` cover.

The existing `generateCover` option will be honored: when false, no optional cover job is submitted; flows whose domain contract requires a thumbnail still require one. Intelligent-cover analysis was rejected for phase one because it adds asynchronous cost and non-determinism.

### Use intelligent tiering and explicit cost controls

New durable objects use intelligent tiering. CDN, CI, COS request, retrieval, and storage metrics will have budget alarms. Application reads use database metadata rather than `LIST Objects` or per-render `HEAD` requests. Automatic CDN refresh, global acceleration, cross-region replication, and bucket versioning remain disabled. Failed-task intermediates expire after 14 days; durable originals and published derivatives have no automatic deletion in this phase.

## Risks / Trade-offs

- [Historical MinIO media becomes unavailable at cutover] -> Treat this as an accepted breaking change and deliver historical migration separately.
- [A seven-day bearer URL is shared] -> Authorize before issuance, never log full signed URLs, store only grant periods, and support targeted emergency object removal/purge.
- [Browser cache retains media after logout] -> Accept for owner-only media under the cost-first policy; sensitive future media can use a shorter rendition-specific policy.
- [No application upload-size ceiling permits costly uploads] -> Keep exact-path request signing, tenant usage accounting, concurrency controls, anomaly alerts, and platform-limit validation without rejecting by business size.
- [Intelligent-tiered objects incur monitoring or retrieval charges] -> Track tier/retrieval cost and rely on CDN caching; change the configured storage class only after measured evidence.
- [Cloud Infinite callback is delayed or duplicated] -> Persist job state, verify callbacks, make completion idempotent, and expose retryable processing status.
- [Real-time CI parameters fragment cache or collide] -> Serve persisted fixed renditions; preserve processing parameters in cache keys for exceptional real-time use.
- [Single-process or multi-instance races issue different URLs] -> Persist grant periods and renew transactionally instead of relying on process memory.
- [COS outage prevents new media persistence] -> Fail and retry through existing durable task semantics; do not silently write MinIO.
- [Pre-release tests and production share one empty bucket] -> Accept the temporary arrangement before public launch, keep unit tests on mocks/fakes, and separate test storage before tests could affect live customer data.

## Migration Plan

1. Verify the shared pre-release bucket and CDN, CAM role and least-privilege policy, lifecycle rules, CORS, Cloud Infinite workflows/callbacks, CDN origin authorization, Type D keys, HTTPS, cache-key policy, browser/node TTLs, and video range-origin rules.
2. Add schema for immutable rendition metadata, upload sessions, processing jobs, and persisted delivery-grant periods without rewriting historical rows.
3. Introduce the COS-only storage facade implementation, internal streaming upload, metadata checks, model-access signing, and diagnostics while keeping existing API response shapes where possible.
4. Add upload-session request-signing APIs and browser multipart upload; switch video decomposition and other browser uploads after end-to-end validation.
5. Add persistent Cloud Infinite image renditions and deterministic video-cover processing, then switch image/video consumers to rendition keys.
6. Switch authorized browser delivery to stable Type D CDN URLs and validate cache reuse, range seeking, expiry, tamper rejection, and permission enforcement.
7. Remove MinIO configuration, SDK usage, local runtime branches, and server-side thumbnail/cover generation from the migrated flows.
8. Observe COS requests, CI jobs, origin traffic, CDN downstream traffic, retrieval charges, failures, and the application server's public bandwidth before declaring rollout complete.

Rollback redeploys the prior MinIO-backed version. Old MinIO objects become readable again, but new COS-only objects created during the rollout will be unavailable to the rolled-back application. No automatic reverse copy or dual-write is provided.

## Open Questions

- What bucket and CDN domain will replace the shared production resources for the test environment after public launch?
