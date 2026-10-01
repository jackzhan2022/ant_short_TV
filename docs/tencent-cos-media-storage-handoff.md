# Tencent COS Media Migration Handoff

Last updated: 2026-10-02

## 1. Handoff Status

- Repository: `D:\信计软件项目\ant_short_TV` (main checkout remains on `master`)
- Worktree: `C:\Users\12775\.codex\worktrees\migrate-media-storage-to-tencent-cos\ant_short_TV`
- Branch: `codex/migrate-media-storage-to-tencent-cos`
- Remote: `origin/codex/migrate-media-storage-to-tencent-cos`
- OpenSpec change: `migrate-media-storage-to-tencent-cos`
- OpenSpec progress: `42/55` (task `1.2` complete after role-authenticated CI verification)
- Latest implementation commit: `8c62da7` (`fix(storage): accept native CI picture result arrays`)
- Implementation commits through `8c62da7` are pushed and deployed. The active release is `/opt/antv/releases/20261002-8c62da7-native-callback`.

All new image-ingestion paths use persisted asynchronous `imageSlim` jobs and publish only ready display renditions. The user's actual inspiration image (record `51`) verifies browser direct upload, a persistent compressed display, authenticated callback recovery, and CDN delivery. A new unpublished smoke image (record `55`) now verifies native post-fix callback acceptance without replay; actual CDN responses also have seven-day browser cache headers. This is still not a fully production-ready release: remaining cloud readiness, video/range/cost evidence, rollback, and the full backend test baseline remain open.

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
- `/opt/antv/current` resolves to `/opt/antv/releases/20261002-8c62da7-native-callback`. `antv.service` is `active/running`, with `ExecMainStatus=0`, `NRestarts=0`, and `/v3/api-docs` returning `200`.
- Server `/opt/antv/shared/env` now specifies `antv-1418200553`, `ap-guangzhou`, `INTELLIGENT_TIERING`, `https://antvcdn.aixmax.cn`, and the callback URL above. The main checkout's ignored `backend/env` still has the legacy bucket and empty region; do not overwrite unrelated user settings. Task `1.1` remains open until both deployed profiles are verified.
- DNS resolves `antvcdn.aixmax.cn` to `antvcdn.aixmax.cn.cdn.dnsv1.com`. Actual signed-object HTTPS delivery, expiry rejection, and cache reuse now succeed; this does not prove all CDN settings or video range behavior.
- The approved `antv-direct-upload` CORS rule allows `https://antv.aixmax.cn`, GET/HEAD/PUT/POST/DELETE, required SDK headers, ETag/CRC64/request-ID exposure, and 600-second preflight caching. PUT preflight with actual upload headers returns `200`; the user's image upload also completed. Full multipart renewal/resume coverage remains open.
- Intelligent tiering is enabled with a 30-day transition. Lifecycle rules now abort incomplete multipart uploads after three days and expire only `uploads/` staging objects after seven days. Versioning is `Off`, and no replication configuration exists. Failed-intermediate cleanup, global acceleration, full browser-upload behavior, and alarms remain unverified.
- `imageSlim` API usage and picture/media queues are enabled; access-time automatic compression remains disabled. Earlier `PicBucketUnBinded` and `401 AccessDenied` blockers were resolved by activation and the approved CI role policy. Do not create duplicate bucket-trigger workflows.
- The user added exact CDN Referer origin `antv.aixmax.cn`, preserving `aixmax.cn` and SHA256 Type D authentication. App-origin signed GETs return `200`; expired and tampered signatures return `403`.
- Backend artifact SHA-256 is recorded below; the unchanged frontend archive SHA-256 is `0BA6EF0C6C0100AA819330509593DDE8A0B3FFAA0FC986B777B7D002D7CB2D8C`. Current diagnostic classes are under `/tmp/20261002-8c62da7-native-callback/`.
- Retained releases include `/opt/antv/releases/20261001-c14a7d3-cdn`, `/opt/antv/releases/20261001-80f1b40-callback`, `/opt/antv/releases/20261001-89efbfc-cos`, and the original MinIO release `/opt/antv/releases/20260929111242-168aee7-storyboard-hotfix`.
- Pre-cutover backups are under `/opt/antv/backups/20261001-89efbfc-cos/`: `env.before`, `env.cutover.before`, `env.scheduler.before`, `database.sql.gz`, and `shared-files.tar.gz` (workflow Skills and review exports), plus previous CORS/lifecycle settings. The dump's 247,727,591 uncompressed bytes and gzip integrity were verified; the shared archive is readable. These snapshots predate later user writes; do not restore them blindly.
- Flyway `V123` through `V127` are applied successfully; the database is at `V127`. The checked execution/analysis/review/decomposition categories have zero active work on the latest read-only check.
- The default single scheduler thread was occupied by subscription grant processing, delaying publication even after media became ready. The approved release now runs with literal shared-env property `spring.task.scheduling.pool.size=4`; publication reconciled successfully after restart. A possible overdue-period loop in `CommercialSubscriptionGrantService` is a separate billing follow-up, not fixed by this storage change.

Remaining environment and acceptance checks:

- Verify both deployed profiles and every required multipart CORS method/header through actual browser upload, renewal, and resume; tasks `1.1` and `1.3` remain open.
- Complete and verify failed-intermediate cleanup after 14 days without targeting durable originals or ready derivatives.
- Verify versioning, cross-region replication, and global acceleration remain disabled.
- Complete CDN private-origin/configuration inspection, cache-key exclusion for only `sign,t`, node-cache behavior, disabled auto-refresh, and coalesced origin requests. The console's all-files node rule is 30 days. The existing media-extension browser rule was corrected from three to seven days; actual responses now have `Cache-Control: max-age=604800`. Full browser cache reuse and seven-day grant acceptance still need evidence.
- Verify the video snapshot/display chain. Native image callback acceptance is now proved by record `55`; the existing user image was previously recovered by replay, without a second compression job.
- Configure video-only range origin and prove a private video returns valid `206 Partial Content` responses.
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
- The backend signs only the assigned object pathname, allowed method, allowed query keys, and allowed signable headers.
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

## 5. Verification Evidence

Recorded verification from the implementation runs (not a claim that the full backend suite passes):

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

The callback and Type D fixes, including `8c62da7`, passed backend packaging, frontend lint/type checking, and antd lint before deployment. No frontend source changed in those fixes.

Deployed backend: `/opt/antv/current/backend/ant-short-tv-backend-0.1.0-SNAPSHOT.jar`.
SHA-256: `72951B3ECCE64CB6F3D221B9079A899E520C35B672165F92D6259BB55B3E93CC` (verified during deployment on 2026-10-02).

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

Full backend `mvn test` is currently blocked by a known branch-baseline authentication test mismatch:

- 1,169 tests ran before the run was stopped.
- 146 failures appeared across 25 controller/integration test classes.
- All observed failures expected HTTP 200 but received HTTP 400.
- Those tests still hard-code registration verification code `123456`, while the existing authentication implementation now generates random SMS verification codes.
- This COS branch did not modify that authentication behavior. Do not mark OpenSpec task `8.3` complete until the baseline suite is repaired or a project owner explicitly accepts a documented exception.
- `AiImageTaskControllerTest` separately reproduced 19 baseline failures. Focused storage/rendition tests passing is not evidence that this full-suite issue is resolved.

## 6. Known Blocking Gaps

### P0: Remaining real-cloud readiness

The user approved the COS configuration/release switch, Flyway, historical MinIO-only media becoming unavailable, CI role access, and the exact application Referer. Deployment and one real-image flow are verified. Tasks `1.1`, `1.3` through `1.7`, and `8.4` through `8.9` remain open for their unverified portions; do not turn partial image evidence into full release readiness.

Seven-day browser headers and native post-fix image callbacks are accepted. Full cache/grant behavior, video covers/ranges, cost alerts/bills, and the rollback drill remain open. Keep the prior releases and matching environments recoverable.

### P0: Existing applied migration and callback compatibility

`V125` and `V127` are now comment-only: historical style rows retain their `style-library/public/.../cover-compressed.jpg` paths, and no schema migration invents unverified derived paths. If an environment applied earlier versions of those files, investigate its Flyway checksums and affected rows before rollout. Restore the old row paths from verified pre-migration evidence or recreate and verify replacement COS objects, then explicitly rebaseline checksums. Checksum repair alone does not repair dangling media paths; do not run blind `flyway repair`.

If an earlier COS build persisted random-token nonterminal jobs, drain them under that build or cancel and requeue them through a controlled recovery procedure before rollout. Their stored hashes cannot reconstruct a bearer token for HMAC-based resubmission. Preserve the Type D key while jobs are active; key rotation requires the same drain/requeue planning and also invalidates existing CDN signatures.

### P1: Full backend test baseline

Task `8.3` remains open for the registration fixture mismatch described above. Do not silently change production authentication to accommodate stale tests or report the affected suite as the full suite.

## 7. Recommended Continuation Order

1. Preserve the verified user image and current release; do not replay its callback or resubmit compression again.
2. Let the user operate a dedicated unpublished MP4 upload in inspiration management, then verify its native snapshot/display callback chain. Complete both-profile readiness and remaining cache configuration separately.
3. Validate backend internal streaming/multipart upload, browser renewal/resume, video ranges and origin bytes, lifecycle targeting, and concurrent public-bandwidth usage. Record evidence per OpenSpec item.
4. Reconcile compression operations, cache hit ratio, COS/CDN traffic, and actual bills; configure and exercise cost alerts before revisiting the persistent-rendition design.
5. Obtain a suitable maintenance window for a rollback drill; take a fresh snapshot first so later user writes are not lost. Record COS-only object limitations under the old release.
6. Repair or explicitly accept the registration-test baseline before closing `8.3`. Track subscription scheduler loop investigation separately; do not silently alter billing logic. Avoid repeating full suites for documentation-only changes.

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
