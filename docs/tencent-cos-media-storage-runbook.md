# Tencent COS Media Storage Runbook

## Runtime Resources

- Bucket: `antv-1418200553`
- Region: `ap-guangzhou`
- Access: private read/write
- Storage class: intelligent tiering
- CDN: `antvcdn.aixmax.cn`
- CDN authentication: Type D, SHA256, hexadecimal `t`, configured lifetime 604800 seconds

The owner explicitly uses the unpublished production deployment for pre-release real-cloud acceptance; no second test instance is required. Provision separate test resources before post-launch testing could affect live customer media. Unit tests remain on mocks/fakes.

## Runtime Configuration And Cutover

The application imports `env` from its backend working directory. The deployed file is `/opt/antv/shared/env`, linked from the release's `backend/env`. The approved COS cutover and readiness fixes are deployed. As rechecked on 2026-10-02, `/opt/antv/current` resolves to `/opt/antv/releases/20261002-5010894-readiness`, `antv.service` is active/running with no restarts, and Flyway `V123` through `V128` are successfully applied. V128 adds per-attempt failed-display cleanup markers only. The backend JAR SHA-256 is `337E11F541720423C4C131189409CD95BBDCF3766FD821132AE1829117C9F60E`; frontend output is unchanged from the previous COS release.

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

Pre-cutover backups are under `/opt/antv/backups/20261001-89efbfc-cos/`, including the database, shared files, and `env.before`/`env.cutover.before`/`env.scheduler.before`. The later real rollback drill has a fresh verified snapshot under `/opt/antv/backups/20261002-rollback-drill/`. Retain previous COS releases and `/opt/antv/releases/20260929111242-168aee7-storyboard-hotfix` with its matching MinIO environment. Never blindly restore old database contents over later writes. Do not include secrets in release archives, command-line arguments, logs, or Git. Task `1.1` is accepted for the owner's approved shared pre-release production deployment.

## Required CAM Scope

The attached `AntvBackendCosRole` needs object read/write/metadata and multipart operations plus Cloud Infinite processing. It now has `QcloudCOSDataFullControl`, `QcloudCOSFullAccess`, `QcloudCOSBucketConfigRead`, and user-approved `QcloudCIFullAccess`. Real CVM-role queue access and successful image processing verify the former `401 AccessDenied` blocker is resolved; task `1.2` is complete. Tighten to bucket-scoped policies after readiness verification. Do not place permanent SecretId or SecretKey values in application configuration.

The browser never receives a SecretId or SecretKey. It requests a short-lived signature for each COS upload request. Object operations use only the pending session's exact pathname. The installed SDK first discovers resumable multipart uploads with bucket-root `GET /`, empty `uploads` and `prefix` equal to that exact assigned key; only this constrained lookup is additionally permitted. Query fields are whitelisted, key pagination cannot leave the assigned key, and every supplied query value is signed. General object listing and shortened/cross-session prefixes stay disallowed. Host/header/role/session ownership/pending/expiry guards remain in force.

## COS Configuration

- Abort incomplete multipart uploads after 3 days.
- Remove unconfirmed staging uploads after 7 days and failed intermediates after 14 days.
- Target staging cleanup only at `uploads/`, not the verified session originals under `materials/.../uploads/...`. Completed session originals are retained for recovery and removed only by reference-aware application cleanup after session expiry when unused.
- Keep durable originals and published derivatives.
- Keep versioning, cross-region replication, and global acceleration disabled.
- Allow CORS only from deployed frontend origins and expose ETag-related headers required by the COS browser SDK.

The initial 2026-10-01 preflight for `https://antv.aixmax.cn` and requested method `PUT` was denied with `403 AccessForbidden` and a COS `CORSResponse` whitelist error. The correction below and actual browser multipart acceptance resolved this; task `1.3` is complete. Preserve unrelated allowlists; do not make the bucket public or allow every origin as a workaround.

After the approved correction, `antv-direct-upload` permits that exact origin and GET/HEAD/PUT/POST/DELETE, required SDK headers including `x-cos-*`, and exposes ETag, request ID, CRC64, and storage class. PUT preflight with actual upload headers returns `200`; real images and 35-MB browser multipart uploads complete. Intelligent tiering and three-day multipart/seven-day `uploads/` staging rules are enabled. Versioning is Off, replication is absent and console global acceleration is off. A retained failed browser session succeeds after the exact-key SDK lookup fix; per-request signing remains scoped and credentials are not disclosed. A browser upload exceeding the 300-second signature period is separate follow-up coverage.

The deployed hourly failed-display cleaner uses job-row locking and READ_COMMITTED metadata checks, exact original/output correlation, a registered READY original, a FAILED job/display at least 14 days old, and per-attempt markers. It never applies a broad `derived/` lifecycle expiration. Its bounded scan has a finite upper ID so invalid low IDs and continuous new arrivals cannot starve later candidates. Actual acceptance deleted only synthetic failed output job `8`, retained its original unchanged and returned false on repeat; keep its marker and retained original auditable. No user object was deleted.

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

Console readback also confirms CDN cache auto-refresh is not configured and COS global acceleration is off. Existing adaptive WebP is on; it was not changed during acceptance. Record any associated representation processing in account usage/billing rather than assuming imageSlim application-job counts cover all image-processing cost.

On 2026-10-02, the existing browser rule for media extensions was changed from three to seven days, preserving its extension list and authentication. Existing and new display responses report `Cache-Control: max-age=604800`, including CDN HITs across changed signatures. The console's all-files node-cache rule is 30 days; cache-key exclusion is exactly `sign,t`, coalescing is on, and range origin is enabled only for `mp4,m4v,mov,webm`. Actual large-video origin and bounded concurrent egress evidence are recorded below. The owner's Chrome Network screenshot proves actual repeat disk-cache reuse and completes `8.5`, alongside stable grants and authentication tests.

The user added exact Referer origin `antv.aixmax.cn` while preserving `aixmax.cn`. Keep SHA256 Type D enabled; do not add wildcard origins or disable authentication. With the application Referer, valid/repeated/changed-signature GETs return `200 / Cache Hit`; expired and tampered signatures return `403`.

Type D uses `sign = SHA256(key + path + t)`, with `path` including the leading slash and `t` encoded as hexadecimal. The console validates expiration at `t + configured lifetime`. Set `OBJECT_STORAGE_CDN_TYPE_D_EXPIRY_SECONDS` to that console lifetime; the signer subtracts it from the persisted grant expiry to derive `t`. `OBJECT_STORAGE_CDN_AUTHORIZATION_SECONDS` separately controls grant duration. Using the grant expiry directly as `t` extends access beyond the intended period. Never print the key or complete signed URL during verification.

Official references: https://cloud.tencent.com/document/product/228/41625 and https://cloud.tencent.com/document/product/228/41622

## Cloud Infinite Configuration

All new generated, uploaded, imported, reference/storyboard, style, and cover image paths now submit persisted asynchronous display jobs. JPEG, PNG, and GIF inputs use `imageSlim` directly and retain their supported output extension. Unsupported inputs such as WebP are decoded by the ImageIO WebP plugin and converted by Cloud Infinite using `imageMogr2/format/png` before `imageSlim`, so their display rendition uses `.png`. Original dimensions and originals are preserved. Thumbnail, detail, and preview roles share only the verified ready display object, with no original fallback.

`CreateMediaJobs` selects the active picture-processing queue automatically; use `DescribePicProcessQueues` for readiness checks rather than configuring a queue ID in the application. The application submits fixed original inputs and immutable `derived/` outputs. Do not add duplicate bucket-trigger processing; derived paths must never retrigger a workflow. Picture queue access, real image/video-cover outputs and native callbacks are verified. Workflow `TotalCount=0` is authoritative; the SDK's one empty placeholder is not a configured workflow.

For this design, keep access-time automatic compression disabled and enable `imageSlim` API usage. The asynchronous picture queue under COS bucket **Tasks And Workflows > Queues And Callbacks** and media processing are enabled. Former `PicBucketUnBinded` and `401 AccessDenied` errors were resolved by activation and the approved CI role policy. A real CVM-role API call is authoritative; do not toggle compression or create duplicate workflows to address stale diagnostics.

Each job supplies its own JSON callback URL containing a domain-separated HMAC-SHA256 bearer token derived from the Type D key, operation, and output key. Persist only its hash and validate the callback's job ID, input key, output key, and `UserData` correlation before changing state. Terminal callbacks are idempotent; process restarts and submission retries reuse the same token. Drain or cancel/requeue nonterminal jobs from earlier random-token implementations before rollout. Do not rotate the Type D key with active jobs without a controlled recovery plan.

Real picture callbacks may contain object/array `JobsDetail`, object/array `Operation.PicProcessResult`, and an empty `ProcessResult.Etag`. The deployed handler accepts both forms at each level, rejecting empty or multiple picture results instead of choosing one. It checks authentication and correlation before COS I/O and HEAD-verifies successful image output length, MIME type, and ETag. If CI wrote STANDARD, an ETag-conditional self-copy converts it to the configured intelligent tiering and verifies unchanged content before READY. Duplicate terminal callbacks do not repeat work. This is terminal output verification, not rendition polling. `8c62da7` adds the native nested-result-array compatibility that the earlier manually constructed replay did not exercise.

Coverless uploaded/imported videos use a verified one-second JPEG snapshot with bounded zero-second fallback, followed by the normal display job. AI videos and episode compositions reuse a ready bound first-frame display instead of decoding the video. `generateCover=false` suppresses optional cover work. There is no synchronous `PicOperations`, rendition HEAD polling, intelligent-cover analysis, or unconditional transcoding in these paths.

## Migration And Rollback Compatibility

Historical style-library media is not backfilled by schema migration. `V125` and `V127` are comment-only, and historical `style-library/public/.../cover-compressed.jpg` paths remain unchanged. If an earlier version already applied those migrations, inspect both Flyway checksums and affected rows. Restore paths from verified pre-migration evidence or create and verify replacement COS objects before explicitly rebaselining checksums. A blind `flyway repair` only changes checksums; it does not fix dangling media references.

The COS cutover does not copy, read, or fall back to historical MinIO-only objects. Rollback restores the prior release together with its matching MinIO environment; investigate schema compatibility and any required database/Skill restoration without discarding post-cutover writes. COS-only objects created after cutover remain unavailable to that release; there is no reverse copy or dual write. The real drill on 2026-10-02 switched the production service to the old release, restored eight old MinIO thumbnails, then restored the COS release and byte-identical current environment. An exact old-release storage-library probe read record `1` and rejected new COS-only `51`/`56`; it is not an old HTTP-status claim. No database rollback or object deletion was performed. Retain needed release directories and both generations of snapshots.

## Release Checks

Verify correct, expired, and tampered Type D URLs; browser request-signature renewal; cross-tenant and cross-object signing denial; CDN HIT behavior across signature changes; video `206 Partial Content`; lifecycle targeting; Cloud Infinite outputs; cost alerts; and absence of media bodies on the application server's public link.

Service checks must use endpoints actually exposed by the candidate. Verify `antv.service` state, `/v3/api-docs` (`200`), and protected `/api/currentUser` (`401` without credentials), rather than assuming `/actuator/health` exists. These checks prove service availability, not the COS upload/processing/delivery workflow.

Task `8.3` is accepted from a completed 1,370-test full backend cohort plus corrected migration-inventory reruns (7 tests): current cohort XML has zero failures/errors, one intentional live-AI skip and no missing reports. Do not describe this as one green full-run exit: the initial run had five stale V128 inventory assertions, corrected in the rerun. Registration fixtures now capture the mock verification code and media fixtures use offline COS/CAM/CI and authenticated callbacks; production authentication/billing are unchanged. Backend packaging and frontend 404 tests, lint/type checking, antd lint and build passed. Real integration, range/egress, browser caching, rollback, alert configuration and readiness tasks are accepted. The owner's manual test receipt and explicit acceptance on 2026-10-02 close `8.7`; all 55 apply tasks are complete and archived at `openspec/changes/archive/2026-10-02-migrate-media-storage-to-tencent-cos`, with seven main specifications synchronized. Integration with the newer `master` is being verified separately; the cloud release remains unchanged.

### Recorded Real Image Check (2026-10-02)

The user's inspiration record `51`, titled `好看的你`, is `IMPORTED / READY / PUBLISHED`. Preserve it, including its original and publication status. Original PNG: `2,351,331` bytes; persistent display: `798,037` bytes (approximately 66% smaller); both are `852 x 1846` and intelligent tiering. Job `1` is `SUCCEEDED`, attempt `1`, total display jobs `1`. Repeated and changed-signature CDN GETs hit cache without submitting another compression job.

The authenticated browser rendered the image at its correct natural dimensions. Nginx recorded the thumbnail authorization endpoint as `302` with zero application response body bytes, confirming CDN media delivery rather than application proxying for this image.

The provider finished compression on 2026-10-01; initial callbacks failed before the deployed payload fix. Verified provider success was replayed through the real authenticated callback endpoint twice, both returning `200`, with no direct READY database mutation or new processing submission. Native callback acceptance is independently proved by the new record below. Generic SDK picture-job parsing incorrectly reports the input as the region; authenticated raw XML confirms input/output/UserData correlation. Do not relax production correlation checks or unnecessarily replay this recovered job again.

### Native Callback Acceptance (2026-10-02)

New browser-uploaded record `55`, `COS 自动回调通过验收 20261002`, remains unpublished. Its original/display sizes are `475,004` / `90,812` bytes, both `512 x 320` and intelligent tiering. Job `5` succeeded on attempt `1`; provider completion was `01:07:16 +0800` and the native Go-client callback returned `200` at `01:07:18 +0800`. The application automatically reached `IMPORTED / READY / UNPUBLISHED` without manual replay or database state overrides. Valid/repeated/changed-signature CDN requests return `200 / Cache Hit` and seven-day browser cache headers; expired/tampered signatures return `403`.

Earlier unpublished diagnostic records `52` through `54` were created before the native nested-array fix; retain their diagnostic distinction from the accepted new job. Temporary callback ingress/proxy instrumentation is removed from active Nginx configuration, the original site configuration is restored, and the diagnostic processes have finished.

### Video And Multipart Acceptance (2026-10-02)

User record `56`, `COS 视频验收 20261002`, is `IMPORTED / READY / PUBLISHED`; preserve the user's publication choice. MP4: `404,184` bytes, intelligent tiering, duration `2.733333` seconds, `1280 x 720`. Tencent one-second synchronous snapshot supplied its persistent `56,600`-byte JPEG original; async picture job `6` compressed it once to `38,998` bytes, same dimensions, with native callback `200` and attempt `1`.

The browser loads/plays/seeks without a media error. App file/thumbnail routes return `302` with zero body bytes. Head/middle/tail 1-KiB ranges return correct `206`/`Content-Range`; repeat and changed signatures HIT, seven-day cache headers remain. Console confirms private HTTPS origin, coalescing on, range off globally and on only for `mp4,m4v,mov,webm`, cache-key exclusion exactly `sign,t`, node rule 30 days and browser media rule seven days.

The generated 35,403,873-byte browser multipart fixture now creates unpublished record `57` through the retained upload session `7`, after deployment of `5010894`. The SDK's exact-key bucket-root lookup is permitted without broadening object access. Session completion has a multipart ETag; original HEAD matches MP4, size and intelligent tiering. A 67,378-byte JPEG cover original is compressed once to 31,657 bytes, both 1280 x 720; native callback job `7` returned 200 at `05:16:46 +0800`, attempt 1. Decomposition completed-session validation, immutable subtitle handling and failed-output cleanup are also deployed and tested.

The 35,403,873-byte private fixture has separately passed actual backend streaming and transfer-manager multipart upload over the same-region internal route, with verified MP4/length/intelligent-tiering metadata and a multipart ETag. This is not browser multipart acceptance. Role-authenticated workflow listing returns `TotalCount=0` and persisted jobs have no derived inputs or nonderived outputs; app-submitted processing is accepted without creating duplicate bucket workflows.

Cold 1-KiB ranges on the 35-MB original at `04:32:05 +0800` returned first MISS then HIT, including changed signatures. Freshly reloaded provider COS monitoring CSV `antv-1418200553-traffic-20261002045245.csv` shows only `13.46 KB` CDN-origin transfer at `04:35`, not the whole source. SHA-256: `a6cae9eb9caf8361de04c997655ea8f2d0e91f967c1373d63b873291b716f628`. The `03:30` internal-upload total `70.82 MB` and public-upload zero reconcile the two backend uploads. Units are decimal and this is delayed monitoring, not billing. Task `1.5` is complete.

Two browser sessions `8`/`9` started at `05:28:41 +0800` and successfully created unpublished records `58`/`59`; both have verified 35,403,873-byte MP4 originals, intelligent tiering and multipart ETags. Cover jobs `9`/`10` each succeeded on attempt 1. Eight CDN workers simultaneously made 32 exact 256-KiB range requests, receiving 8,388,608 bytes with valid 206/Content-Range. A 35-second header-only capture of outbound IPv4 HTTPS on `eth0` recorded 19,769 original frame bytes, average 0.00452 Mbps, peak one-second 0.09250 Mbps and zero kernel drops. It covers the overlapping uploads through `05:28:49`, not the second upload's final five seconds. Nginx create/sign/complete responses are control-plane bodies only (296-879 bytes), not media. Task `8.6` accepts this bounded egress sample alongside real browser playback/seeking and cold-origin evidence; it is not a sustained capacity benchmark or whole-account bandwidth bill.

Anonymous file/thumbnail requests return 401. Existing media grants remain revision 1 and exactly seven days with unchanged expiry/updated timestamps despite repeated authorized access. CDN expired/tampered URLs return 403, changed signatures HIT. The owner's 2026-10-02 Chrome screenshot, after warming images and normal reload with Disable cache unchecked, shows two display-image requests: status 200, type WebP, thumbnail initiator, Size `(disk cache)`, times 4 ms / 2 ms. The earlier first-load image transferred about 174 KB / 496 ms, content length 173,018 bytes and seven-day cache headers. Repeated reads now use browser disk bytes, not another CDN body. Existing CDN adaptive WebP accounts for the delivered representation; the immutable original/display PNG objects are unchanged. A cached first-response CDN MISS header is not evidence against subsequent browser disk-cache reuse. Task `8.5` is complete. Sanitized local proof: `.temp/cos-release/browser-disk-cache-verified-20261002.png`; the signed-name column is removed. Do not commit original header screenshots or signed URLs/cookies.

## Cost Evidence After Cutover

The owner retained persistent compressed display objects. Tencent's published image-processing price is `0.1 CNY / 1,000` successful `imageSlim` operations; use the actual account bill/resource-package deductions as authoritative, not this list price or application job counts alone. Download-time processing repeats charges when requests actually reach CI, whereas direct access to a stored compressed object without processing parameters adds no further compression operation. Browser/CDN cache hits do not reach CI.

Record daily accepted originals, successful display jobs and retries, billable CI compression operations, COS original/derivative bytes and requests, CDN downstream/origin bytes and hit ratio, and application public-egress bytes. Separate the tiny smoke-test sample from normal traffic and allow for delayed usage/billing data. Check CDN cache-key exclusion for only `sign,t`, seven-day browser and 30-day node freshness, and coalesced origin requests before using these numbers to compare designs. Cost/bandwidth alarm configuration and the owner's manual notification acceptance are complete; continue delayed billing reconciliation as an operational follow-up, not an assumption that an empty bill proves free usage.

The owner confirmed and the console saved a combined 100-CNY/month COS/CDN/CI budget, only notifying the current Tencent main account, no automatic shutdown or new recipients, plus a 4-Mbps CVM outbound alert. Task `1.7` is complete. Budget `ANTV-Media-COS-CDN-CI-Monthly` starts October 2026 continuously, includes only `p_ci`, `p_cos`, `p_cdn` account-wide, and alerts when actual expense-bill total cost exceeds 100 CNY. All component charges and resource-package purchases count toward this combined total; there is no separate request-count quota or automated cutoff. Receiver: existing main account only; email/SMS/site inbox, all days 08:00-22:00 Asia/Shanghai.

Enabled CVM policy `policy-mshkaobq` (`ANTV-CVM-Public-Egress-4Mbps`) binds only `ins-6l48r6io` / `43.138.147.3` in Guangzhou. Condition: public outbound bandwidth >4 Mbps, one-minute granularity, one consecutive point; abnormal repeat notifications once per hour. It reuses the unchanged system notification template, whose sole receiver is the existing main account, email/SMS, all days 00:00-23:59:59 Asia/Shanghai. No event rule or autoscaling action is attached; the old default policy is unchanged. If the console refuses a no-tag draft after `DescribeEffectivePolicy` reports an absent policy, remove the unused empty tag draft row, not CAM permissions or tag policies; independent readback must confirm the saved policy.

October cost analysis currently shows 1.10 CNY, entirely the previously purchased 100,000-operation picture-compression package, not a nine-job metered charge. Its exact deduction details through yesterday show one `imageSlim` operation and one operation deducted (1:1), consistent with the user's October 1 provider completion. The rounded 0.00% summary is not zero actual use. Today's additional jobs, account-wide adaptive WebP/read activity, and current COS/CDN postpaid rows still need reconciliation; those rows are not yet billed. Unbilled entries and 0.00 estimates do not establish free traffic/processing. No package purchase, refund or renewal setting was changed.

The console warns that current data excludes unbilled postpaid usage and previous-month costs finalize after 12:00 on the second day. The owner performed manual testing, reported receiving the test notification, and explicitly accepted the final gate on 2026-10-02. Task `8.7` is closed from this owner acceptance plus the recorded telemetry/configuration evidence. The precise notification category/channel was not supplied; do not claim that both budget/CVM paths or every email/SMS channel were independently observed. No new notification test, production threshold change or cleanup was performed by the agent. Template `notice-dor0k4ew` remained unchanged and owner-only at inspection. Cleanup of user-created TEST configurations is not independently verified; stop/remove those without altering the official 100-CNY/4-Mbps settings. Continue delayed usage/package-deduction/postpaid reconciliation without treating missing charges as zero. The real MinIO rollback drill is already complete; do not repeat it without another approved maintenance window.

Official billing reference: https://cloud.tencent.com/document/product/460/58117
