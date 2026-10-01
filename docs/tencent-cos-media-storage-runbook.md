# Tencent COS Media Storage Runbook

## Runtime Resources

- Bucket: `antv-1418200553`
- Region: `ap-guangzhou`
- Access: private read/write
- Storage class: intelligent tiering
- CDN: `antvcdn.aixmax.cn`
- CDN authentication: Type D, SHA256, hexadecimal `t`, configured lifetime 604800 seconds

Pre-release production and test deployments intentionally share these resources. Provision separate test resources before post-launch testing could affect live customer media.

## Runtime Configuration And Cutover

The application imports `env` from its backend working directory. The deployed file is `/opt/antv/shared/env`, linked from the release's `backend/env`. The approved COS cutover and callback/signing fixes are deployed. As rechecked on 2026-10-02, `/opt/antv/current` resolves to `/opt/antv/releases/20261001-c14a7d3-cdn`, `antv.service` is active/running with no restarts, and Flyway `V123` through `V127` are successfully applied.

The server now uses the following non-secret settings, preserving the Type D key and unrelated variables. The main checkout's ignored `backend/env` still contains the legacy bucket and empty region; do not treat it as a verified deployed profile or overwrite unrelated user settings.

```dotenv
OBJECT_STORAGE_BUCKET=antv-1418200553
OBJECT_STORAGE_REGION=ap-guangzhou
OBJECT_STORAGE_CLASS=INTELLIGENT_TIERING
OBJECT_STORAGE_CDN_DOMAIN=https://antvcdn.aixmax.cn
OBJECT_STORAGE_CDN_TYPE_D_EXPIRY_SECONDS=604800
OBJECT_STORAGE_CDN_AUTHORIZATION_SECONDS=604800
OBJECT_STORAGE_VIDEO_RENEWAL_THRESHOLD_SECONDS=7200
OBJECT_STORAGE_UPLOAD_SIGNATURE_SECONDS=300
OBJECT_STORAGE_CI_CALLBACK_URL=https://antv.aixmax.cn/api/media-processing/callbacks/tencent-ci
spring.task.scheduling.pool.size=4
```

The scheduler pool property is intentionally literal. The default single scheduler thread was occupied by subscription-period processing and delayed publication after media was ready; increasing capacity allowed reconciliation. A suspected overdue-period loop is a separate billing follow-up, not repaired here.

Pre-cutover backups are under `/opt/antv/backups/20261001-89efbfc-cos/`, including the database, shared files, and `env.before`/`env.cutover.before`/`env.scheduler.before`. Retain previous COS releases and `/opt/antv/releases/20260929111242-168aee7-storyboard-hotfix` with its matching MinIO environment. These snapshots predate later user writes: take a fresh backup before a rollback drill and never blindly restore an old database. Do not include secrets in release archives, command-line arguments, logs, or Git. Validate both deployed profiles before completing OpenSpec task `1.1`.

## Required CAM Scope

The attached `AntvBackendCosRole` needs object read/write/metadata and multipart operations plus Cloud Infinite processing. It now has `QcloudCOSDataFullControl`, `QcloudCOSFullAccess`, `QcloudCOSBucketConfigRead`, and user-approved `QcloudCIFullAccess`. Real CVM-role queue access and successful image processing verify the former `401 AccessDenied` blocker is resolved; task `1.2` is complete. Tighten to bucket-scoped policies after readiness verification. Do not place permanent SecretId or SecretKey values in application configuration.

The browser never receives a SecretId or SecretKey. It requests a short-lived signature for each COS upload request. The backend signs only the pending session's exact object pathname and the required put, multipart initiate/upload/list/complete/abort, and head requests after validating query keys and headers.

## COS Configuration

- Abort incomplete multipart uploads after 3 days.
- Remove unconfirmed staging uploads after 7 days and failed intermediates after 14 days.
- Target staging cleanup only at `uploads/`, not the verified session originals under `materials/.../uploads/...`. Completed session originals are retained for recovery and removed only by reference-aware application cleanup after session expiry when unused.
- Keep durable originals and published derivatives.
- Keep versioning, cross-region replication, and global acceleration disabled.
- Allow CORS only from deployed frontend origins and expose ETag-related headers required by the COS browser SDK.

The initial 2026-10-01 preflight for `https://antv.aixmax.cn` and requested method `PUT` was denied with `403 AccessForbidden` and a COS `CORSResponse` whitelist error, including when no custom requested headers were supplied. The correction below resolved that probe, but task `1.3` remains open for its other required checks. Inspect existing rules and preserve unrelated allowlists; do not make the bucket public or allow every origin as a workaround.

After the approved correction, `antv-direct-upload` permits that exact origin and GET/HEAD/PUT/POST/DELETE, required SDK headers including `x-cos-*`, and exposes ETag, request ID, CRC64, and storage class. PUT preflight with the actual upload headers returns `200`, and the user's actual image completed direct upload and delivery. Intelligent tiering and the three-day multipart/seven-day `uploads/` staging rules are enabled. Failed-intermediate cleanup, multipart renewal/resume, and other task `1.3` checks remain open.

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

These are target settings, not a claim that all are verified. Actual display responses on 2026-10-02 report `Cache-Control: max-age=259200` (three days); the seven-day browser target is still open, as are complete node-cache, origin, range, and coalescing checks.

The user added exact Referer origin `antv.aixmax.cn` while preserving `aixmax.cn`. Keep SHA256 Type D enabled; do not add wildcard origins or disable authentication. With the application Referer, valid/repeated/changed-signature GETs return `200 / Cache Hit`; expired and tampered signatures return `403`.

Type D uses `sign = SHA256(key + path + t)`, with `path` including the leading slash and `t` encoded as hexadecimal. The console validates expiration at `t + configured lifetime`. Set `OBJECT_STORAGE_CDN_TYPE_D_EXPIRY_SECONDS` to that console lifetime; the signer subtracts it from the persisted grant expiry to derive `t`. `OBJECT_STORAGE_CDN_AUTHORIZATION_SECONDS` separately controls grant duration. Using the grant expiry directly as `t` extends access beyond the intended period. Never print the key or complete signed URL during verification.

Official references: https://cloud.tencent.com/document/product/228/41625 and https://cloud.tencent.com/document/product/228/41622

## Cloud Infinite Configuration

All new generated, uploaded, imported, reference/storyboard, style, and cover image paths now submit persisted asynchronous display jobs. JPEG, PNG, and GIF inputs use `imageSlim` directly and retain their supported output extension. Unsupported inputs such as WebP are decoded by the ImageIO WebP plugin and converted by Cloud Infinite using `imageMogr2/format/png` before `imageSlim`, so their display rendition uses `.png`. Original dimensions and originals are preserved. Thumbnail, detail, and preview roles share only the verified ready display object, with no original fallback.

`CreateMediaJobs` selects the active picture-processing queue automatically; use `DescribePicProcessQueues` for readiness checks rather than configuring a queue ID in the application. The application submits fixed original inputs and immutable `derived/` outputs. Do not add duplicate bucket-trigger processing; derived paths must never retrigger a workflow. Picture queue access and one real output are verified; native post-fix callback delivery and video processing remain release checks.

For this design, keep access-time automatic compression disabled and enable `imageSlim` API usage. The asynchronous picture queue under COS bucket **Tasks And Workflows > Queues And Callbacks** and media processing are enabled. Former `PicBucketUnBinded` and `401 AccessDenied` errors were resolved by activation and the approved CI role policy. A real CVM-role API call is authoritative; do not toggle compression or create duplicate workflows to address stale diagnostics.

Each job supplies its own JSON callback URL containing a domain-separated HMAC-SHA256 bearer token derived from the Type D key, operation, and output key. Persist only its hash and validate the callback's job ID, input key, output key, and `UserData` correlation before changing state. Terminal callbacks are idempotent; process restarts and submission retries reuse the same token. Drain or cancel/requeue nonterminal jobs from earlier random-token implementations before rollout. Do not rotate the Type D key with active jobs without a controlled recovery plan.

Real picture callbacks may contain a single-object `JobsDetail` and an empty `ProcessResult.Etag`. The deployed handler accepts object/array forms, checks authentication and correlation before COS I/O, and HEAD-verifies successful image output length, MIME type, and ETag. If CI wrote STANDARD, an ETag-conditional self-copy converts it to the configured intelligent tiering and verifies unchanged content before READY. Duplicate terminal callbacks do not repeat work. This is terminal output verification, not rendition polling.

Coverless uploaded/imported videos use a verified one-second JPEG snapshot with bounded zero-second fallback, followed by the normal display job. AI videos and episode compositions reuse a ready bound first-frame display instead of decoding the video. `generateCover=false` suppresses optional cover work. There is no synchronous `PicOperations`, rendition HEAD polling, intelligent-cover analysis, or unconditional transcoding in these paths.

## Migration And Rollback Compatibility

Historical style-library media is not backfilled by schema migration. `V125` and `V127` are comment-only, and historical `style-library/public/.../cover-compressed.jpg` paths remain unchanged. If an earlier version already applied those migrations, inspect both Flyway checksums and affected rows. Restore paths from verified pre-migration evidence or create and verify replacement COS objects before explicitly rebaselining checksums. A blind `flyway repair` only changes checksums; it does not fix dangling media references.

The COS cutover does not copy, read, or fall back to historical MinIO-only objects. Rollback restores the prior release together with its matching MinIO environment; investigate schema compatibility and any required database/Skill restoration without discarding post-cutover writes. COS-only objects created after cutover remain unavailable to that release; there is no reverse copy or dual write. Retain all needed release directories until rollback is verified. The rollback drill has not yet been performed.

## Release Checks

Verify correct, expired, and tampered Type D URLs; browser request-signature renewal; cross-tenant and cross-object signing denial; CDN HIT behavior across signature changes; video `206 Partial Content`; lifecycle targeting; Cloud Infinite outputs; cost alerts; and absence of media bodies on the application server's public link.

Service checks must use endpoints actually exposed by the candidate. Verify `antv.service` state, `/v3/api-docs` (`200`), and protected `/api/currentUser` (`401` without credentials), rather than assuming `/actuator/health` exists. These checks prove service availability, not the COS upload/processing/delivery workflow.

The affected backend suites and frontend suite/build have recorded passing results, but OpenSpec task `8.3` remains open because full backend controller tests have a pre-existing registration-fixture mismatch (`123456` versus random SMS verification codes). Tasks `8.4` through `8.9` require real integration, caching, range/cost, release/rollback, and readiness evidence; do not close them from unit-test or key-deployment results.

### Recorded Real Image Check (2026-10-02)

The user's inspiration record `51`, titled `好看的你`, is `IMPORTED / READY / PUBLISHED`. Preserve it, including its original and publication status. Original PNG: `2,351,331` bytes; persistent display: `798,037` bytes (approximately 66% smaller); both are `852 x 1846` and intelligent tiering. Job `1` is `SUCCEEDED`, attempt `1`, total display jobs `1`. Repeated and changed-signature CDN GETs hit cache without submitting another compression job.

The authenticated browser rendered the image at its correct natural dimensions. Nginx recorded the thumbnail authorization endpoint as `302` with zero application response body bytes, confirming CDN media delivery rather than application proxying for this image.

The provider finished compression on 2026-10-01; initial callbacks failed before the deployed payload fix. Verified provider success was replayed through the real authenticated callback endpoint twice, both returning `200`, with no direct READY database mutation or new processing submission. A new native callback still needs acceptance evidence. Generic SDK picture-job parsing incorrectly reports the input as the region; authenticated raw XML confirms input/output/UserData correlation. Do not relax production correlation checks or unnecessarily replay this recovered job again.

## Cost Evidence After Cutover

The owner retained persistent compressed display objects. Tencent's published image-processing price is `0.1 CNY / 1,000` successful `imageSlim` operations; use the actual account bill/resource-package deductions as authoritative, not this list price or application job counts alone. Download-time processing repeats charges when requests actually reach CI, whereas direct access to a stored compressed object without processing parameters adds no further compression operation. Browser/CDN cache hits do not reach CI.

Record daily accepted originals, successful display jobs and retries, billable CI compression operations, COS original/derivative bytes and requests, CDN downstream/origin bytes and hit ratio, and application public-egress bytes. Separate the tiny smoke-test sample from normal traffic and allow for delayed usage/billing data. Check CDN cache-key exclusion for only `sign,t`, seven-day browser and 30-day node freshness, and coalesced origin requests before using these numbers to compare designs. Cost alarms and real traffic evidence remain open; do not infer them from an empty bill or a passing preflight.

Official billing reference: https://cloud.tencent.com/document/product/460/58117
