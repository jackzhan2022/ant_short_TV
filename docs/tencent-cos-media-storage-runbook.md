# Tencent COS Media Storage Runbook

## Runtime Resources

- Bucket: `antv-1418200553`
- Region: `ap-guangzhou`
- Access: private read/write
- Storage class: intelligent tiering
- CDN: `antvcdn.aixmax.cn`
- CDN authentication: Type D

Pre-release production and test deployments intentionally share these resources. Provision separate test resources before post-launch testing could affect live customer media.

## Runtime Configuration And Cutover

The application imports `env` from its backend working directory. The deployed file is `/opt/antv/shared/env`, linked from the release's `backend/env`. On 2026-10-01, only `OBJECT_STORAGE_CDN_TYPE_D_KEY` was updated and the old service restarted; the active release remains `20260929111242-168aee7-storyboard-hotfix`. This is not a COS release deployment.

Read-only preflight found `OBJECT_STORAGE_BUCKET=ant-short-tv` and an explicitly empty `OBJECT_STORAGE_REGION` in both the shared server environment and the user's local `backend/env`. These legacy values override the new defaults. Use the following non-secret settings for the approved COS deployment, preserving the real Type D key and all unrelated variables:

```dotenv
OBJECT_STORAGE_BUCKET=antv-1418200553
OBJECT_STORAGE_REGION=ap-guangzhou
OBJECT_STORAGE_CLASS=INTELLIGENT_TIERING
OBJECT_STORAGE_CDN_DOMAIN=https://antvcdn.aixmax.cn
OBJECT_STORAGE_CDN_AUTHORIZATION_SECONDS=604800
OBJECT_STORAGE_VIDEO_RENEWAL_THRESHOLD_SECONDS=7200
OBJECT_STORAGE_UPLOAD_SIGNATURE_SECONDS=300
OBJECT_STORAGE_CI_CALLBACK_URL=https://antv.aixmax.cn/api/media-processing/callbacks/tencent-ci
```

Do not change the old MinIO-backed release's bucket separately. Back up its complete environment, database, and workflow Skill files, then coordinate the COS settings with the matched frontend/backend release switch. Retain the old environment for rollback; the key-update backup is not a substitute for a fresh cutover backup. Do not include secrets in release archives, command-line arguments, logs, or Git. Validate both deployed profiles before completing OpenSpec task `1.1`.

## Required CAM Scope

Attach the `AntvBackendCosRole` instance role to the backend. It needs object read/write/metadata and multipart operations plus Cloud Infinite processing operations. Actual CAM inspection on 2026-10-01 found only `QcloudCOSDataFullControl`, `QcloudCOSFullAccess`, and `QcloudCOSBucketConfigRead`; the previously documented `QcloudCIFullAccess` association does not exist. Real CI requests return `401 AccessDenied`. Obtain explicit approval before adding CI access, then verify queue discovery and job submission from the CVM. Tighten to bucket-scoped policies after readiness verification. Do not place permanent SecretId or SecretKey values in application configuration.

The browser never receives a SecretId or SecretKey. It requests a short-lived signature for each COS upload request. The backend signs only the pending session's exact object pathname and the required put, multipart initiate/upload/list/complete/abort, and head requests after validating query keys and headers.

## COS Configuration

- Abort incomplete multipart uploads after 3 days.
- Remove unconfirmed staging uploads after 7 days and failed intermediates after 14 days.
- Target staging cleanup only at `uploads/`, not the verified session originals under `materials/.../uploads/...`. Completed session originals are retained for recovery and removed only by reference-aware application cleanup after session expiry when unused.
- Keep durable originals and published derivatives.
- Keep versioning, cross-region replication, and global acceleration disabled.
- Allow CORS only from deployed frontend origins and expose ETag-related headers required by the COS browser SDK.

The initial 2026-10-01 preflight for `https://antv.aixmax.cn` and requested method `PUT` was denied with `403 AccessForbidden` and a COS `CORSResponse` whitelist error, including when no custom requested headers were supplied. The correction below resolved that probe, but task `1.3` remains open for its other required checks. Inspect existing rules and preserve unrelated allowlists; do not make the bucket public or allow every origin as a workaround.

After the approved correction, `antv-direct-upload` permits that exact origin and GET/HEAD/PUT/POST/DELETE, required SDK headers including `x-cos-*`, and exposes ETag, request ID, CRC64, and storage class. PUT preflight with the actual upload headers returns `200`. Intelligent tiering and the three-day multipart/seven-day `uploads/` staging rules are enabled. This is partial environment readiness, not a successful upload/processing/delivery integration test; failed-intermediate cleanup and other task `1.3` checks remain open.

Read-only Windows reproduction (the object need not exist; this creates no object):

```powershell
curl.exe --max-time 15 --silent --show-error --dump-header - --request OPTIONS 'https://antv-1418200553.cos.ap-guangzhou.myqcloud.com/uploads/cos-readiness-probe/source.png' --header 'Origin: https://antv.aixmax.cn' --header 'Access-Control-Request-Method: PUT'
```

After updating CORS, repeat for the actual SDK upload headers and every required multipart method, and verify exposed response headers before closing the task. A successful header-free probe alone is not full browser-upload readiness.

## CDN Configuration

- Private COS origin authorization and HTTPS enabled.
- Node cache: 30 days.
- Browser cache: 7 days.
- Cache key ignores Type D `sign` and `t` only; media-processing parameters remain significant.
- Cache auto-refresh disabled and coalesced origin requests enabled.
- Range origin requests enabled for durable video extensions only.

## Cloud Infinite Configuration

All new generated, uploaded, imported, reference/storyboard, style, and cover image paths now submit persisted asynchronous display jobs. JPEG, PNG, and GIF inputs use `imageSlim` directly and retain their supported output extension. Unsupported inputs such as WebP are decoded by the ImageIO WebP plugin and converted by Cloud Infinite using `imageMogr2/format/png` before `imageSlim`, so their display rendition uses `.png`. Original dimensions and originals are preserved. Thumbnail, detail, and preview roles share only the verified ready display object, with no original fallback.

`CreateMediaJobs` selects the active picture-processing queue automatically; use `DescribePicProcessQueues` for readiness checks rather than configuring a queue ID in the application. The application submits fixed original inputs and immutable `derived/` outputs. Do not add duplicate bucket-trigger processing; derived paths must never retrigger a workflow. Real queue permissions, outputs, and callback delivery remain release checks.

For this design, keep access-time automatic compression disabled and enable `imageSlim` API usage. Separately enable the asynchronous picture queue under COS bucket **Tasks And Workflows > Queues And Callbacks**. The COS console currently shows `queue-pic-process-1` and the media queues in use; an earlier CI view showed no queues. A real CVM-role API call is authoritative: `PicBucketUnBinded` indicated missing queue activation; the current `401 AccessDenied` instead indicates missing role access. Do not keep toggling compression or create duplicate workflows to work around that denial.

Each job supplies its own JSON callback URL containing a domain-separated HMAC-SHA256 bearer token derived from the Type D key, operation, and output key. Persist only its hash and validate the callback's job ID, input key, output key, and `UserData` correlation before changing state. Terminal callbacks are idempotent; process restarts and submission retries reuse the same token. Drain or cancel/requeue nonterminal jobs from earlier random-token implementations before rollout. Do not rotate the Type D key with active jobs without a controlled recovery plan.

Coverless uploaded/imported videos use a verified one-second JPEG snapshot with bounded zero-second fallback, followed by the normal display job. AI videos and episode compositions reuse a ready bound first-frame display instead of decoding the video. `generateCover=false` suppresses optional cover work. There is no synchronous `PicOperations`, rendition HEAD polling, intelligent-cover analysis, or unconditional transcoding in these paths.

## Migration And Rollback Compatibility

Historical style-library media is not backfilled by schema migration. `V125` and `V127` are comment-only, and historical `style-library/public/.../cover-compressed.jpg` paths remain unchanged. If an earlier version already applied those migrations, inspect both Flyway checksums and affected rows. Restore paths from verified pre-migration evidence or create and verify replacement COS objects before explicitly rebaselining checksums. A blind `flyway repair` only changes checksums; it does not fix dangling media references.

The COS cutover does not copy, read, or fall back to historical MinIO-only objects. Rollback restores the prior release together with its matching MinIO environment and any required matching database/Skill snapshot. COS-only objects created after cutover remain unavailable to that release; there is no reverse copy or dual write. Retain both release directories until rollback is verified.

## Release Checks

Verify correct, expired, and tampered Type D URLs; browser request-signature renewal; cross-tenant and cross-object signing denial; CDN HIT behavior across signature changes; video `206 Partial Content`; lifecycle targeting; Cloud Infinite outputs; cost alerts; and absence of media bodies on the application server's public link.

Service checks must use endpoints actually exposed by the candidate. The old release returns `404` for `/actuator/health`; verify `antv.service` state, `/v3/api-docs` (`200`), and protected `/api/currentUser` (`401` without credentials) instead of declaring startup failed from that `404` alone. These checks prove service availability, not the COS upload/processing/delivery workflow.

The affected backend suites and frontend suite/build have recorded passing results, but OpenSpec task `8.3` remains open because full backend controller tests have a pre-existing registration-fixture mismatch (`123456` versus random SMS verification codes). Tasks `8.4` through `8.9` require real integration, caching, range/cost, release/rollback, and readiness evidence; do not close them from unit-test or key-deployment results.

## Cost Evidence After Cutover

The owner retained persistent compressed display objects. Tencent's published image-processing price is `0.1 CNY / 1,000` successful `imageSlim` operations; use the actual account bill/resource-package deductions as authoritative, not this list price or application job counts alone. Download-time processing repeats charges when requests actually reach CI, whereas direct access to a stored compressed object without processing parameters adds no further compression operation. Browser/CDN cache hits do not reach CI.

Record daily accepted originals, successful display jobs and retries, billable CI compression operations, COS original/derivative bytes and requests, CDN downstream/origin bytes and hit ratio, and application public-egress bytes. Separate the tiny smoke-test sample from normal traffic and allow for delayed usage/billing data. Check CDN cache-key exclusion for only `sign,t`, seven-day browser and 30-day node freshness, and coalesced origin requests before using these numbers to compare designs. Cost alarms and real traffic evidence remain open; do not infer them from an empty bill or a passing preflight.

Official billing reference: https://cloud.tencent.com/document/product/460/58117
