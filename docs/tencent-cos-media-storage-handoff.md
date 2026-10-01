# Tencent COS Media Migration Handoff

Last updated: 2026-10-02

## 1. Handoff Status

- Repository: `D:\信计软件项目\ant_short_TV` (main checkout remains on `master`)
- Worktree: `C:\Users\12775\.codex\worktrees\migrate-media-storage-to-tencent-cos\ant_short_TV`
- Branch: `codex/migrate-media-storage-to-tencent-cos`
- Remote: `origin/codex/migrate-media-storage-to-tencent-cos`
- OpenSpec change: `migrate-media-storage-to-tencent-cos`
- OpenSpec progress: `52/55`; only `1.7`, `8.5`, and `8.7` remain open for cost alerts/billing and actual browser cache-transfer evidence.
- Latest implementation commit: `5010894` (`fix(storage): complete multipart and immutable media cleanup contracts`).
- Implementation commits through `5010894` are pushed and deployed. The active release is `/opt/antv/releases/20261002-5010894-readiness`.

All new image-ingestion paths use persisted asynchronous `imageSlim` jobs and publish only ready display renditions. User records `51` and `56` remain unchanged. Native callbacks, real 35-MB browser multipart uploads, backend internal uploads, failed-output cleanup, video ranges, bounded concurrent application egress, and real rollback are accepted. This is not a fully completed OpenSpec release: cost alerts/billing and actual browser cache-transfer evidence remain open.

## 2. Confirmed Product Contract

1. Keep every accepted original image in private COS.
2. Create exactly one persistent original-resolution display rendition for normal display.
3. JPEG, PNG, and GIF use `imageSlim` directly.
4. Unsupported source formats, including WebP, are converted to PNG first and then processed once with `imageSlim`.
5. Lists, detail pages, thumbnails, and normal previews use only the ready display rendition. They must not fall back to the original image.
6. The original is exposed only through an explicit authorized download action.
7. Do not use unrestricted real-time image-processing query parameters. Persistent derived objects are required to keep processing cost and cache keys bounded.

## 3. Tencent Cloud Environment

Known deployment values:

| Item | Value |
| --- | --- |
| Backend domain | `antv.aixmax.cn` |
| Backend CVM public IP | `43.138.147.3` |
| COS bucket | `antv-1418200553` |
| Region | `ap-guangzhou` |
| CDN domain | `antvcdn.aixmax.cn` |
| CVM role | `qcs::cam::uin/100047744245:roleName/AntvBackendCosRole` |
| CI callback | `https://antv.aixmax.cn/api/media-processing/callbacks/tencent-ci` |

Completed in the console:

- The bucket and CVM are in the same Tencent Cloud account.
- The CVM instance role `AntvBackendCosRole` is attached.
- The role has `QcloudCOSDataFullControl`, `QcloudCOSFullAccess`, `QcloudCOSBucketConfigRead`, and now `QcloudCIFullAccess`. The user approved the CI association; actual CVM-role queue access and image-job processing succeed. Task `1.2` is complete; least-privilege tightening remains a follow-up.
- Permanent `SecretId`/`SecretKey`, `GetFederationToken`, and `AssumeRole` are not part of the selected design.

Server and cloud state, including read-only rechecks on 2026-10-02:

- SSH alias `antv-prod` authenticates successfully and passwordless `sudo` works. The dedicated deployment key named in the general runbook is absent; an existing configured identity provides access.
- The Type D key was copied from the user's ignored local `backend/env` without displaying it; the value was preserved through cutover. The earlier key-only backup is `/opt/antv/shared/env.cos-key.20261001T104015Z.bak`.
- `/opt/antv/current` resolves to `/opt/antv/releases/20261002-5010894-readiness`. `antv.service` is `active/running`, with `ExecMainStatus=0`, `NRestarts=0`, and `/v3/api-docs` returning `200`. The readiness deployment automatically restores the previous COS release on failure; it completed successfully.
- Server `/opt/antv/shared/env` specifies `antv-1418200553`, `ap-guangzhou`, `INTELLIGENT_TIERING`, `https://antvcdn.aixmax.cn`, and the callback URL above. The owner explicitly confirmed real-cloud acceptance should use this unpublished production deployment, not a second test instance. Task `1.1` is complete under that approved scope. The main checkout's ignored `backend/env` still has the legacy bucket and empty region; do not overwrite unrelated user settings.
- DNS resolves `antvcdn.aixmax.cn` to `antvcdn.aixmax.cn.cdn.dnsv1.com`. Actual signed-object HTTPS delivery, expiry rejection, and cache reuse now succeed; this does not prove all CDN settings or video range behavior.
- The approved `antv-direct-upload` CORS rule allows `https://antv.aixmax.cn`, GET/HEAD/PUT/POST/DELETE, required SDK headers, ETag/CRC64/request-ID exposure, and 600-second preflight caching. PUT preflight with actual upload headers returns `200`; the user's image upload also completed. Full multipart renewal/resume coverage remains open.
- Intelligent tiering is enabled with a 30-day transition. Lifecycle rules abort incomplete multipart uploads after three days and expire only `uploads/` staging objects after seven days. Versioning is `Off`, no replication configuration exists, and console readback confirms global acceleration is off. The deployed transactional cleaner removes only correlated FAILED display outputs after 14 days, never registered READY originals. Its actual synthetic-object acceptance is recorded below; task `1.3` is complete. Alarms remain pending.
- `imageSlim` API usage and picture/media queues are enabled; access-time automatic compression remains disabled. Earlier `PicBucketUnBinded` and `401 AccessDenied` blockers were resolved by activation and the approved CI role policy. Do not create duplicate bucket-trigger workflows.
- The user added exact CDN Referer origin `antv.aixmax.cn`, preserving `aixmax.cn` and SHA256 Type D authentication. App-origin signed GETs return `200`; expired and tampered signatures return `403`.
- COS domain/transfer console confirms private-origin and CDN authentication on, HTTPS configured, CDN cache auto-refresh not configured, and global acceleration off. Existing CDN adaptive WebP is enabled; no setting was changed during this inspection. Include its potential representation-processing usage in cost accounting instead of equating application imageSlim-job counts with the whole account bill.
- Actual CVM-role workflow listing reports authoritative `TotalCount=0`; the SDK returned one empty list placeholder, not a configured workflow. After the new multipart/concurrency checks, nine provider picture jobs have succeeded on attempt 1; job `8` is a deliberately synthetic FAILED cleanup fixture with no provider job. No input contains `/derived/`, all outputs are derived, and native image/video-cover callbacks have succeeded. Tasks `1.4` and `1.6` are accepted without adding duplicate bucket triggers. The acceleration API diagnostic returns UNKNOWN, so do not substitute it for the console's verified off state.
- Backend artifact SHA-256 is recorded below; the unchanged frontend archive SHA-256 is `0BA6EF0C6C0100AA819330509593DDE8A0B3FFAA0FC986B777B7D002D7CB2D8C`. Current diagnostic classes and unpacked release dependencies are under `/tmp/20261002-5010894-readiness/`.
- Retained releases include `/opt/antv/releases/20261001-c14a7d3-cdn`, `/opt/antv/releases/20261001-80f1b40-callback`, `/opt/antv/releases/20261001-89efbfc-cos`, and the original MinIO release `/opt/antv/releases/20260929111242-168aee7-storyboard-hotfix`.
- Pre-cutover backups are under `/opt/antv/backups/20261001-89efbfc-cos/`: `env.before`, `env.cutover.before`, `env.scheduler.before`, `database.sql.gz`, and `shared-files.tar.gz` (workflow Skills and review exports), plus previous CORS/lifecycle settings. The dump's 247,727,591 uncompressed bytes and gzip integrity were verified; the shared archive is readable. These snapshots predate later user writes; do not restore them blindly.
- Flyway `V123` through `V128` are applied successfully; the database is at `V128`. V128 adds only per-attempt failed-output cleanup markers. The checked execution/analysis/review/decomposition categories have zero active work on the latest read-only check.
- The default single scheduler thread was occupied by subscription grant processing, delaying publication even after media became ready. The approved release now runs with literal shared-env property `spring.task.scheduling.pool.size=4`; publication reconciled successfully after restart. A possible overdue-period loop in `CommercialSubscriptionGrantService` is a separate billing follow-up, not fixed by this storage change.

Remaining environment and acceptance checks:

- Browser records `57` through `59` now verify actual 35,403,873-byte multipart uploads, multipart ETags, completion HEAD verification, intelligent tiering, native cover callbacks, and unpublished domain state. Session `7` was retained across the earlier failure and succeeded after deployment. Per-request authorization is exercised; a browser upload lasting beyond the 300-second signature period has not been separately load-tested.
- Failed-output cleanup and cloud lifecycle targeting are accepted; preserve the synthetic fixture's retained original and audit marker.
- Configuration inspection is accepted: exact `sign,t` exclusion preserves other parameters, node rule 30 days, browser media rule seven days, disabled auto-refresh and enabled coalescing. Actual responses have `max-age=604800`. Full browser cache reuse and seven-day grant acceptance still need evidence for `8.5`.
- The video snapshot/display chain and native image callback are accepted through records `55`/`56`; the original user image was previously recovered by replay, without a second compression job.
- Video-only range origin and cold-large-video transfer are accepted; see provider-origin telemetry below. Bounded concurrent egress is accepted, not a long-term capacity benchmark.
- Configure cost and usage alarms for COS, CDN, Cloud Infinite, retrieval, requests, storage, and application public bandwidth.

## 4. Implemented Foundation

### Storage and credentials

- Replaced MinIO runtime storage with Tencent COS and removed MinIO/local enablement branches from migrated paths.
- Added one lifecycle-managed COS client using CVM instance-role credentials.
- Added streaming and multipart backend uploads, reads, metadata inspection, deletion, and task-bounded presigned model URLs.
- Added COS diagnostics and metrics with credential/query redaction.
- Original PUT and multipart completion prohibit overwrite, including the TransferManager path whose SDK otherwise drops custom headers. Copy retries reuse a conflicting destination only after source ETag/length/type and destination metadata plus ETag or CRC64 evidence prove it is the same content.

### Browser direct upload

- Added authenticated upload sessions and per-request COS authorization for `cos-js-sdk-v5`.
- The backend signs exact assigned object operations, allowed methods/query keys/headers, and the SDK's narrowly constrained bucket-root multipart lookup with an exact assigned-key prefix. General object listing and broadened prefixes remain forbidden.
- The signer now requires the exact configured bucket host: `${bucket}.cos.${region}.myqcloud.com`.
- Create-object requests require the configured `INTELLIGENT_TIERING` storage class, and completion verifies the actual storage class returned by COS.
- Completion verifies staging object length, ETag, MIME type, and storage class with `HEAD Object`.
- A verified staging object is copied server-side to an immutable `materials/.../v1/original.ext` key with an ETag precondition. The business session stores only the final key. A still-valid old upload signature can therefore overwrite only an unreferenced staging object.
- The staging source is intentionally left for lifecycle cleanup so a database transaction failure does not destroy retryability.
- Inspiration image creation copies the completed `materials/{tenant}[/project]/uploads/{month}/{session}/v1/original.ext` with an ETag precondition and retains that session object for retries. Reference-aware lifecycle cleanup may remove an unused completed session original after its seven-day session expiry; generic `uploads/` cleanup must never remove accepted business originals.
- Publishing a failed inspiration image explicitly retries its display job from the registered original. The image stays hidden until the verified rendition reaches `READY`; scheduler ticks only reconcile persisted state and never submit retries.
- Frontend inspiration and decomposition pages retain the upload session and COS task handle across failures, resume the same multipart task, and retry backend completion separately. Removed files and cancellation races cancel their outstanding handles.
- Inspiration image selection uploads the actual original without client resizing or compression.

### Delivery and media metadata

- Added immutable media-object/rendition metadata and seven-day delivery-grant persistence.
- Added deterministic Type D CDN URL derivation and separate COS presigned URLs for external AI providers.
- Real CDN compatibility fix `c14a7d3` signs `SHA256(key + path + hexadecimal_timestamp)`. Tencent expires URLs at `t + configured lifetime`, so `t` is the persisted grant expiry minus `OBJECT_STORAGE_CDN_TYPE_D_EXPIRY_SECONDS` (default `604800`), not the expiry itself. This setting must match the console; grant duration is configured separately.
- Style-library delivery now also persists seven-day grants, retaining the existing public-access policy: guest subject `0`, authenticated subject the actual user ID.
- Multimodal video separates the browser display reference from the provider's original-image access URL.
- Updated frontend consumers to prefer display/thumbnail/cover delivery URLs rather than durable provider URLs.
- Regenerated the OpenAPI client; the meaningful generated diffs are `mediaUploadController.ts` and `typings.d.ts`. Other generated files may show only line-ending status and must not be hand-edited.

### Cloud Infinite foundation

- Added `ImageDisplayRenditionPlanner`, `MediaObjectRegistry`, processing-job persistence, callback token hashing, and callback correlation checks.
- Added fixed `imageSlim` rules and `imageMogr2/format/png|imageSlim` for unsupported formats.
- Terminal callback handling is now idempotent for both `SUCCEEDED` and `FAILED`; a later opposite terminal callback cannot rewrite the result.
- AI-generated image results register their verified original, submit the persisted display job, and defer task and saved-variant publication until callback-confirmed rendition readiness.
- Generated, saved-variant, reference/storyboard, browser-managed/imported inspiration, and new style-library images all use that persistent display contract. WebP originals decode through the pinned ImageIO WebP plugin.
- Imported/uploaded videos use a verified one-second JPEG snapshot with bounded zero-second fallback, then submit the image display job. Generated video and episode covers reuse ready first-frame displays; `generateCover=false` is honored.
- Production storage no longer uses synchronous `PicOperations` or rendition HEAD polling.
- Internal AI-image phases `SETTLING` and `RENDERING` are exposed as `RUNNING`; running filters, cancellation, and superseding cover both phases. Transactional, claim-guarded publication can resume after an already committed domain publication.
- Callback bearer tokens are stable, domain-separated HMAC-SHA256 values derived from the Type D key and operation/output key. Only their hashes are stored; retries after process restart use the same token.
- Real callback fix `80f1b40` accepts single-object or array `JobsDetail`. A valid correlated image success always HEAD-verifies output bytes, MIME type, and ETag, including when Tencent's `ProcessResult.Etag` is empty. If CI wrote STANDARD, an ETag-conditional server-side self-copy aligns the output with intelligent tiering and verifies unchanged content. Token/correlation checks precede COS I/O; duplicate terminal callbacks do not repeat verification or processing.
- Native callback fix `8c62da7` also accepts single-object or one-element-array `Operation.PicProcessResult`. Real native callbacks use arrays at both levels; the earlier replay used an object for the nested result. Empty or multiple result arrays are rejected during binding rather than selecting an arbitrary output. No global Jackson/security settings were relaxed.

### Readiness safeguards (`5010894`)

- Allow the SDK's exact-key bucket-root multipart lookup; 22 signing tests preserve method/query/header/host/ownership/expiry restrictions.
- Decomposition validates completed tenant-owned upload sessions instead of rejecting their promoted immutable key with an obsolete staging-prefix check.
- Subtitle creation allocates its row ID before constructing its immutable key; updates use new UUID keys and preserve prior bytes and unrelated storyboard bindings.
- Failed-display cleanup holds the job row lock across delete and per-attempt marker persistence, uses READ_COMMITTED metadata reads to avoid retry lock inversion, and scans hourly in bounded batches with a finite upper ID to prevent starvation. It accepts the real AI producer's distinct logical/physical version identities. The focused cleanup suite has 53 tests; related combined coverage has 78 tests.
- Registration fixtures request verification and capture the real mock SMS code. Offline media fixtures use fake COS/CAM/CI and authenticated callbacks, not real cloud calls or direct READY overrides. Production authentication and billing behavior are unchanged.

## 5. Verification Evidence

Recorded verification from the implementation runs. Backend acceptance combines the completed full-cohort run with corrected migration-inventory reruns; it is not a single green full-run exit:

```text
Combined affected backend suite after async video-cover integration
Tests run: 211 across 47 classes; Failures: 0, Errors: 0, Skipped: 0

Style delivery-grant follow-up
Tests run: 24; Failures: 0, Errors: 0, Skipped: 0

Final AI-image phase/publication regressions
Tests run: 37 across 6 classes; Failures: 0, Errors: 0, Skipped: 0

Final storage/copy/upload-session regressions
Tests run: 27 across 3 classes; Failures: 0, Errors: 0, Skipped: 0

Real callback regressions (80f1b40)
Tests run: 28 across 4 classes; Failures: 0, Errors: 0, Skipped: 0

Real Type D regressions (c14a7d3)
Tests run: 30 across 4 classes; Failures: 0, Errors: 0, Skipped: 0

Native callback payload/service/signing regressions (8c62da7)
Tests run: 28 across 3 classes; Failures: 0, Errors: 0, Skipped: 0
MediaProcessingJobCoordinatorPersistenceTest: 1 test; Failures: 0, Errors: 0
Before the fix, the native-array payload test reproduced MismatchedInputException.

mvn -DskipTests package (after c358a20)
BUILD SUCCESS; compiled 711 main and 283 test sources

npm run test
Test Files: 76 passed; Tests: 404 passed

npm run lint
Exit 0; 3 existing direct document.cookie warnings

npx antd lint ./src
Exit 0; 11 existing usage/deprecation warnings

npm run build
Webpack compiled successfully

git diff --check
Exit 0
```

The fresh full backend run executed 1,370 tests, initially with only five stale V128 migration-inventory assertions failing. Four corrected classes reran 7 tests successfully. An independent aggregation of the current XML reports for the full run's 281 unique classes confirms 1,370 testcase nodes, zero failures/errors, one intentional live-AI skip, and no missing reports. The mixed JUnit/ArchUnit class produces 282 reporting groups. Ignore older reports outside that cohort. Verification helper: ignored `.temp/cos-release/verify-backend-results.ps1`; rerun evidence: `backend/target/migration-head-regression-green.log`. Backend packaging succeeded before deployment; frontend 404 tests, lint/type checks, antd lint and build passed with only the existing warnings. Task `8.3` is complete.

The callback and Type D fixes, including `8c62da7`, passed backend packaging, frontend lint/type checking, and antd lint before deployment. No frontend source changed in those fixes.

Deployed backend: `/opt/antv/current/backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar`.
SHA-256: `337E11F541720423C4C131189409CD95BBDCF3766FD821132AE1829117C9F60E` (verified during deployment on 2026-10-02).

### Real user-image acceptance evidence (2026-10-02)

The user's image titled `好看的你`, `inspiration_creation.id=51`, is not the generated smoke-test fixture. Preserve its objects, metadata, and publication status.

| Evidence | Observed result |
| --- | --- |
| Original | PNG, `2,351,331` bytes, `852 x 1846`, intelligent tiering |
| Persistent display | `798,037` bytes, `852 x 1846`, intelligent tiering; approximately 66% fewer bytes |
| Business state | `IMPORTED / READY / PUBLISHED` |
| Processing | Job `1`, `SUCCEEDED`, attempt `1`; total display-job count `1` |
| Valid / repeat / changed-signature CDN GET | `200 / Cache Hit`, content length `798037` |
| Expired / tampered signature | `403` |
| Browser cache | Corrected from `max-age=259200` to `max-age=604800`; actual valid/repeat/changed-signature responses verified |
| Browser rendering | Image complete, natural dimensions `852 x 1846` in authenticated management view |
| Application thumbnail endpoint | `302`, application response body `0` bytes; media delivered by CDN |

Tencent completed the one compression at `2026-10-01T20:54:25+0800`. Its initial callbacks failed before `80f1b40`; recovery validated raw provider XML correlation and the stored token hash, then replayed the same authenticated success twice (both `200`). There was no direct database READY mutation and no new compression submission. Native callback acceptance is separately proved below. CDN reads did not increase job count or attempts; actual billable CI usage and traffic costs remain to be reconciled with Tencent billing.

The SDK's generic picture-job response incorrectly reports the input as `ap-guangzhou`; raw authenticated XML from `/pic_jobs/` confirms the real input matches. Do not weaken production correlation checks based on that SDK parsing issue.

### Native Callback And Cache Acceptance (2026-10-02)

On the deployed `8c62da7` release, a new browser upload created unpublished record `55`, `COS 自动回调通过验收 20261002`. Its original is `475,004` bytes and display is `90,812` bytes, both `512 x 320` and intelligent tiering. Job `5` is `SUCCEEDED`, attempt `1`, with exactly one display job. The provider completed at `01:07:16 +0800`; Nginx recorded its Go-client POST at `01:07:18 +0800` as `200`. Domain state became `IMPORTED / READY / UNPUBLISHED` without replay, database state overrides, or resubmission.

Valid/repeated/changed-signature GETs for the new display return `200 / Cache Hit`, `Content-Length: 90812`, and `Cache-Control: max-age=604800`; expired/tampered signatures return `403`. The existing user display also returns the seven-day header without another compression. Browser CDN configuration screenshot: ignored local `.temp/cos-release/browser-cache-7-days-20261002.jpg`.

During diagnosis, records `52` through `54` were created unpublished and their initial callbacks failed on the old nested-result DTO. Treat them as smoke-test records, not user media, and keep them unpublished; do not count their old failures as failures of the accepted new job. Temporary ingress/proxy diagnostics were stopped, the original Nginx site configuration was restored and byte-compared with its backup, and the diagnostic include was moved out of the active configuration. No diagnostic listener remains required.

### User Video Acceptance (2026-10-02)

The user uploaded record `56`, `COS 视频验收 20261002`. Its MP4 is `404,184` bytes, intelligent tiering. Tencent synchronous `getSnapshot` at one second (bounded zero-second fallback) supplied a persistent JPEG cover original of `56,600` bytes, `1280 x 720`; asynchronous picture job `6` produced its `38,998`-byte display, same dimensions and intelligent tiering. Job `6` succeeded on attempt `1`, with a native callback `200` at `01:16:28 +0800`. Business state is `IMPORTED / READY / PUBLISHED`; the agent did not change the user's publication state.

The browser loaded, played, and sought this `2.733333`-second video at `1280 x 720`, readyState `4`, no media error. File and thumbnail authorization routes each returned `302`, body `0` bytes. Five 1-KiB head/repeat/middle/changed-signature/tail requests each returned `206`, exact `Content-Range` and 1024 received bytes. First request was MISS; subsequent ones were HIT; every response had `max-age=604800`. Console readback confirms coalesced origin requests enabled and range origin globally disabled except `mp4,m4v,mov,webm`. The additional large-origin/concurrency evidence below completes `1.5` and `8.6`. Screenshot: ignored `.temp/cos-release/video-playback-verified-20261002.jpg`.

### Backend Internal Upload Acceptance (2026-10-02)

Using the deployed storage facade, its real CAM credential provider, production transfer-manager configuration and same-region internal endpoint, an ignored diagnostic fixture of `35,403,873` bytes was uploaded by both streaming and multipart paths. Both HEAD-verifications matched bytes, `video/mp4`, intelligent tiering and nonempty ETag; the multipart result had a multipart ETag. The internal-endpoint gauge was `1.0`. These are private, nonbusiness acceptance originals under `materials/0/cos_acceptance/202610/991001/`, not user media or application publication. No source video was buffered as one application byte array. This does not establish public-CDN cold-origin or concurrency egress behavior.

### Large Video, Multipart, Cleanup And Concurrency (2026-10-02)

Cold CDN ranges against a 35,403,873-byte private original at `04:32:05 +0800` returned exact 1-KiB `206` responses: first MISS, then middle/repeat/changed-signature HIT. After reloading the COS monitoring page, the exported provider CSV reports only `13.46 KB` of CDN-origin transfer in the `04:35` interval, not a full 35-MB origin fetch. Decimal units apply; monitoring is delayed usage telemetry, not a bill. File: `antv-1418200553-traffic-20261002045245.csv`, SHA-256 `a6cae9eb9caf8361de04c997655ea8f2d0e91f967c1373d63b873291b716f628`, retained in Downloads. The earlier `03:30` interval reports `70.82 MB` internal uploads and zero public uploads, reconciling the two backend test uploads. Task `1.5` is complete.

On deployed `5010894`, retained browser session `7` created unpublished record `57`, `COS 分片验收 20261002`, at `05:16:41 +0800`. Its original HEAD matches `35,403,873` bytes, MP4 and intelligent tiering; completed session evidence has a multipart ETag. The one-second snapshot original is `67,378` bytes, `1280 x 720`; native picture job `7` compressed it to `31,657` bytes, same dimensions, attempt `1`. The native callback returned `200` at `05:16:46 +0800`, automatically reaching `IMPORTED / READY / UNPUBLISHED`. No callback replay or publication change was performed. Screenshot: ignored `.temp/cos-release/browser-multipart-success-20261002.jpg`.

The minimal Spring/MyBatis acceptance helper registers only storage, transaction and cleanup beans, not business schedulers. It created a separate synthetic READY original and 15-day-old FAILED display/job under `materials/0/cos_acceptance/202610/991002/`. Calling the real proxied cleanup returned true, the repeated call false, persisted one attempt marker for job `8`, removed only the failed display, and HEAD-confirmed identical original size/ETag. No user media was deleted. The FAILED row and retained original remain auditable. Tasks `1.3` and `8.4` are complete.

Two browser uploads started together at `05:28:41 +0800` and completed as sessions `8`/`9`, creating unpublished records `58`/`59`; both original HEADs and multipart ETags match the 35,403,873-byte fixture. Cover jobs `9`/`10` each succeeded on attempt 1. During the overlapping uploads, eight CDN workers made 32 exact 256-KiB `206` requests, transferring `8,388,608` bytes; a prior bounded warm-up run transferred the same amount. A 35-second outbound `eth0` capture filtered IPv4 TCP source port 443 and saved only 54-byte headers: 61 packets, 19,769 original frame bytes, zero kernel drops, average `0.00452 Mbps`, peak one-second `0.09250 Mbps`. Observed packets span `05:28:24` to `05:28:49 +0800`, covering the uploads' overlap but not the last five seconds of the second completion. This proves low application HTTPS egress during the bounded concurrent sample, not whole-account bandwidth, every final response, or future capacity. Nginx shows only 200 control-plane responses of 296-879 body bytes for create/authorization/completion, never video bodies. Task `8.6` is complete with this explicitly bounded evidence. Ignored pcap and read-only analyzer: `.temp/cos-release/concurrent-egress.pcap` and `analyze-egress.mjs`; neither captures HTTP bodies or credentials.

Anonymous current-user, published video-file and user-image-thumbnail requests return `401`. Existing image/video grants remain revision `1`, with unchanged created/updated timestamps and exactly seven-day expiry periods despite repeated authorized access. Expired/tampered CDN URLs return `403`, and changed signatures HIT. Task `8.5` stays open because this does not independently prove an actual browser repeat used memory/disk cache without another CDN body transfer; the current browser automation exposes no network-transfer timing.

### Real Rollback Drill (2026-10-02)

The owner explicitly approved a 20-minute maintenance window and testing on the unpublished production deployment. A fresh verified snapshot is `/opt/antv/backups/20261002-rollback-drill/`: database gzip contains `247,754,578` uncompressed bytes (`49,715,943` compressed), plus current environment and shared-file backup. This is distinct from the older cutover snapshot.

The actual service switched to `/opt/antv/releases/20260929111242-168aee7-storyboard-hotfix`; only bucket/region were restored to their verified legacy settings, retaining all unrelated current environment values. The authenticated browser loaded eight MinIO thumbnails (`1` through `8`) with nonzero natural dimensions. A separate read-only probe using that exact old release's storage classes and verified legacy environment read record `1`, but rejected new COS-only records `51` and `56` with `BusinessException`. This probe establishes the old storage boundary; it is not an old-release HTTP status claim.

The script restored `/opt/antv/releases/20261002-8c62da7-native-callback` and byte-identical current environment on exit; startup API returned `200`, service is active/running, ExecMainStatus `0`, NRestarts `0`. Record `56` remained `IMPORTED / READY / PUBLISHED`, its original/cover objects remained present, and job `6` stayed attempt `1`. No database snapshot was restored and no COS object was deleted. Task `8.8` is complete. Old-image browser proof: ignored `.temp/cos-release/rollback-minio-20261002.jpg`.

The earlier registration-fixture baseline failure is resolved in tests only. The old interrupted 1,169-test/146-failure run and separate 19-failure AI-image run are historical diagnosis, not current verification. The full-cohort plus corrected inventory rerun evidence above is authoritative.

## 6. Known Blocking Gaps

### P0: Remaining real-cloud readiness

The user approved the COS configuration/release switch, Flyway, historical MinIO-only media becoming unavailable, CI role access, the exact application Referer, shared pre-release acceptance on production, and the completed real rollback drill. Only tasks `1.7`, `8.5`, and `8.7` remain open. Keep previous releases and matching environments recoverable; the single approved rollback window has already been used and must not be repeated without a new window.

Cost alert defaults are awaiting owner confirmation: 100 CNY/month for COS/CDN/CI, notification only to the current Tencent main account, no automatic shutdown/new recipients, and 4 Mbps CVM bandwidth alert. Do not create or test notification policies without confirming these settings. Provider usage/billing, retrieval/request/storage costs and actual alert delivery still need reconciliation. Actual browser memory/disk-cache body reuse also needs a network-transfer observation; seven-day headers and stable grant rows alone are insufficient.

### P0: Existing applied migration and callback compatibility

`V125` and `V127` are now comment-only: historical style rows retain their `style-library/public/.../cover-compressed.jpg` paths, and no schema migration invents unverified derived paths. If an environment applied earlier versions of those files, investigate its Flyway checksums and affected rows before rollout. Restore the old row paths from verified pre-migration evidence or recreate and verify replacement COS objects, then explicitly rebaseline checksums. Checksum repair alone does not repair dangling media paths; do not run blind `flyway repair`.

If an earlier COS build persisted random-token nonterminal jobs, drain them under that build or cancel and requeue them through a controlled recovery procedure before rollout. Their stored hashes cannot reconstruct a bearer token for HMAC-based resubmission. Preserve the Type D key while jobs are active; key rotation requires the same drain/requeue planning and also invalidates existing CDN signatures.

### P1: Residual Test Coverage

The backend test baseline is repaired without production authentication changes. Report its full-cohort plus focused-rerun provenance accurately. Native browser uploads crossing the 300-second signature expiry and longer sustained concurrency remain follow-up coverage, not claims made by the short acceptance sample.

## 7. Recommended Continuation Order

1. Preserve the verified user image and current release; do not replay its callback or resubmit compression again.
2. Confirm the cost alert amounts, recipients and 4-Mbps bandwidth threshold, then configure and verify notification delivery without shutdown actions.
3. Capture actual browser memory/disk-cache reuse for the unchanged signed image, preserving CDN authentication and the user's media.
4. Reconcile successful processing operations, adaptive-WebP usage, cache hit ratio, COS/CDN traffic, requests/storage/retrieval and actual bills before closing `1.7`/`8.7` or revisiting the persistent-rendition design.
5. Retain the completed rollback evidence and all matching snapshots/releases; never restore old database contents over later writes. Split test resources before public customers make shared acceptance unsafe.
6. Track subscription scheduler loop investigation separately; do not silently alter billing logic. Avoid repeating full suites for documentation-only changes. Do not merge/archive or report 55/55 while the three readiness items remain open.

## 8. Commands to Resume

```powershell
cd C:\Users\12775\.codex\worktrees\migrate-media-storage-to-tencent-cos\ant_short_TV
openspec status --change "migrate-media-storage-to-tencent-cos" --json
openspec instructions apply --change "migrate-media-storage-to-tencent-cos" --json
git status --short --branch
openspec validate migrate-media-storage-to-tencent-cos --strict
```

The local frontend preview is `http://localhost:8034`; its proxy defaults to local backend port `8080`, not the production host. It is not proof of the real COS workflow. After code changes, run checks in proportion to the affected paths; never edit generated services manually:

```powershell
cd backend
mvn -DskipTests clean package

cd ..\frontend
npm run test
npm run lint
npx antd lint ./src
npm run build

cd ..
git diff --check
```

## 9. Security and Secret Handling

- Never commit the Type D key, CAM credentials, security tokens, COS authorization headers, callback bearer tokens, or complete signed URLs.
- The callback URL base is configuration; each processing job appends its stable, single-purpose HMAC bearer token, and only the token hash is stored. Never log the full callback URL.
- Broad COS and approved CI access are attached. Tightening to least privilege remains a follow-up and must not remove the exact upload, copy, metadata, CI job, callback-output, and delivery operations used by the application.
