# Tencent COS Media Migration Handoff

Last updated: 2026-10-01

## 1. Handoff Status

- Repository: `D:\信计软件项目\ant_short_TV` (main checkout remains on `master`)
- Worktree: `C:\Users\12775\.codex\worktrees\migrate-media-storage-to-tencent-cos\ant_short_TV`
- Branch: `codex/migrate-media-storage-to-tencent-cos`
- Remote: `origin/codex/migrate-media-storage-to-tencent-cos`
- OpenSpec change: `migrate-media-storage-to-tencent-cos`
- OpenSpec progress: `42/55`
- Latest implementation commit: `c358a20` (`fix(storage): preserve processing and upload recovery`)
- Implementation commits through `c358a20` are local; they have not been pushed or deployed.

All new image-ingestion paths now use persisted asynchronous `imageSlim` jobs and publish only ready display renditions. Upload recovery is wired through the actual frontend callers. OpenSpec task `6.3` is complete. This is still not a production-ready release: cloud readiness, real integration, cache/range/cost evidence, release/rollback, and the full backend test baseline remain open.

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
- The role currently has `QcloudCOSFullAccess` and `QcloudCIFullAccess`.
- Permanent `SecretId`/`SecretKey`, `GetFederationToken`, and `AssumeRole` are not part of the selected design.

Server state verified on 2026-10-01:

- SSH alias `antv-prod` authenticates successfully and passwordless `sudo` works. The dedicated deployment key named in the general runbook is absent; an existing configured identity provides access.
- The user's ignored `D:\信计软件项目\ant_short_TV\backend\env` contains one nonempty Type D key. Only that key was atomically copied into `/opt/antv/shared/env`, preserving other variables, ownership, and permissions; local/remote value hashes matched without printing the value.
- Recoverable environment backup: `/opt/antv/shared/env.cos-key.20261001T104015Z.bak`.
- The existing `antv.service` was restarted after the key update. It remains `active/running`, with `ExecMainStatus=0`, `NRestarts=0`, `/v3/api-docs` returning `200`, and protected `/api/currentUser` returning `401` without authentication.
- The active release is still `/opt/antv/releases/20260929111242-168aee7-storyboard-hotfix`. No COS branch backend or frontend artifact has been uploaded or switched. `/actuator/health` is not exposed by this old release; its `404` is not a startup failure.
- Read-only preflight found legacy `OBJECT_STORAGE_BUCKET=ant-short-tv` and an explicitly empty `OBJECT_STORAGE_REGION` in both the server environment and the user's local `backend/env`. The server callback URL is correct, but its CDN-domain/storage-class overrides are absent. An empty region overrides the new application's default and fails configuration validation. Do not change the old release's bucket in isolation; switch the COS configuration together with the approved release and preserve the previous environment for rollback.
- DNS resolves `antvcdn.aixmax.cn` to `antvcdn.aixmax.cn.cdn.dnsv1.com`. This alone does not prove CDN authentication, caching, or range configuration.
- COS `OPTIONS` preflight for `Origin: https://antv.aixmax.cn` and requested method `PUT` returns `403 AccessForbidden` with `CORSResponse: This CORS request is not allowed`. The same probe against an object path without custom requested headers is also denied. The current CORS rules do not accept this origin/method pair, so browser direct upload is blocked before authorization can be exercised. No object was created by these read-only probes.
- The unsigned CDN HTTPS root probe reaches the CDN and returns `403`; it is not a signed-object authentication or cache test.
- The release filesystem has approximately 16 GiB available.

Still required before production integration testing:

- Apply the COS runtime configuration in the storage runbook together with the release switch, retaining the configured Type D key. Verify both deployed profiles; task `1.1` remains open.
- Complete COS CORS rules for the real frontend origins and required multipart methods/headers.
- Complete lifecycle rules: abort incomplete multipart uploads after 3 days, delete unconfirmed `uploads/` staging objects after 7 days, and delete failed intermediates after 14 days.
- Verify versioning, cross-region replication, and global acceleration remain disabled.
- Verify CDN HTTPS, private-origin authorization, Type D authentication, cache-key exclusion for only `sign,t`, 30-day node caching, 7-day browser caching, disabled auto-refresh, and coalesced origin requests.
- Verify the active Cloud Infinite picture queue and real authenticated callback routing. Application-submitted jobs already supply fixed original inputs and `derived/` outputs; do not add a duplicate bucket-trigger workflow.
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

Built backend candidate: `backend/target/ant-short-tv-backend-0.1.0-SNAPSHOT.jar`.
SHA-256: `DB0C6BF2BA9D2888594322EAEC46798550D5DE92BA0F7B2AE7518C176C138646`.

Full backend `mvn test` is currently blocked by a known branch-baseline authentication test mismatch:

- 1,169 tests ran before the run was stopped.
- 146 failures appeared across 25 controller/integration test classes.
- All observed failures expected HTTP 200 but received HTTP 400.
- Those tests still hard-code registration verification code `123456`, while the existing authentication implementation now generates random SMS verification codes.
- This COS branch did not modify that authentication behavior. Do not mark OpenSpec task `8.3` complete until the baseline suite is repaired or a project owner explicitly accepts a documented exception.
- `AiImageTaskControllerTest` separately reproduced 19 baseline failures. Focused storage/rendition tests passing is not evidence that this full-suite issue is resolved.

## 6. Known Blocking Gaps

### P0: Real cloud readiness and release approval

Tasks `1.1`, `1.3` through `1.7`, and `8.4` through `8.9` require real environment evidence. Deploying the Type D key did not deploy the migration. Obtain explicit approval before switching the production release, running Flyway, and making historical MinIO-only media unavailable. Keep the existing release and its matching environment for rollback.

### P0: Existing applied migration and callback compatibility

`V125` and `V127` are now comment-only: historical style rows retain their `style-library/public/.../cover-compressed.jpg` paths, and no schema migration invents unverified derived paths. If an environment applied earlier versions of those files, investigate its Flyway checksums and affected rows before rollout. Restore the old row paths from verified pre-migration evidence or recreate and verify replacement COS objects, then explicitly rebaseline checksums. Checksum repair alone does not repair dangling media paths; do not run blind `flyway repair`.

If an earlier COS build persisted random-token nonterminal jobs, drain them under that build or cancel and requeue them through a controlled recovery procedure before rollout. Their stored hashes cannot reconstruct a bearer token for HMAC-based resubmission. Preserve the Type D key while jobs are active; key rotation requires the same drain/requeue planning and also invalidates existing CDN signatures.

### P1: Full backend test baseline

Task `8.3` remains open for the registration fixture mismatch described above. Do not silently change production authentication to accommodate stale tests or report the affected suite as the full suite.

## 7. Recommended Continuation Order

1. Verify runtime configuration, cloud readiness, migration history, and callback compatibility without changing the running old release.
2. Obtain approval for the coordinated COS configuration/release switch and accepted historical-media breakage; back up the database, shared environment, and workflow Skill files together.
3. Publish the verified commit and deploy matched frontend/backend artifacts through the versioned-release procedure. Preserve the previous release and its matching environment.
4. Execute browser upload, backend internal upload, image/cover callback, CDN authentication/cache, video range, and cost checks in dedicated smoke-test resources. Record evidence per OpenSpec item.
5. Perform the rollback drill and document COS-only objects being unavailable to the old release. Mark production readiness only after these checks pass.
6. Repair or explicitly accept the existing registration-test baseline before closing `8.3`; do not repeat already passed full suites for documentation-only changes.

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
- The current broad COS/CI managed policies are accepted for initial readiness. Tightening to least privilege remains a follow-up and must not remove the exact upload, copy, metadata, CI job, callback-output, and delivery operations used by the application.
