# Tencent COS Media Migration Handoff

Last updated: 2026-09-30

## 1. Handoff Status

- Repository: `D:\需求文档\ant_short_TV`
- Worktree: `D:\需求文档\ant_short_TV\.worktrees\migrate-media-storage-to-tencent-cos`
- Branch: `codex/migrate-media-storage-to-tencent-cos`
- Remote: `origin/codex/migrate-media-storage-to-tencent-cos`
- OpenSpec change: `migrate-media-storage-to-tencent-cos`
- OpenSpec progress: `42/55`
- Starting commit before this handoff commit: `edc404c`

This branch is a continuation point, not a production-ready release. The storage, upload-signing, CDN-delivery, and rendition foundations are present, but the asynchronous `imageSlim` workflow is not yet connected to every real image-ingestion path. Several OpenSpec items currently marked complete must be re-audited after that integration is finished.

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

Still required before production integration testing:

- Configure `OBJECT_STORAGE_CDN_TYPE_D_KEY` in the production runtime secret environment. Never commit its value.
- Complete COS CORS rules for the real frontend origins and required multipart methods/headers.
- Complete lifecycle rules: abort incomplete multipart uploads after 3 days, delete unconfirmed `uploads/` staging objects after 7 days, and delete failed intermediates after 14 days.
- Verify versioning, cross-region replication, and global acceleration remain disabled.
- Verify CDN HTTPS, private-origin authorization, Type D authentication, cache-key exclusion for only `sign,t`, 30-day node caching, 7-day browser caching, disabled auto-refresh, and coalesced origin requests.
- Configure video-only range origin and prove a private video returns valid `206 Partial Content` responses.
- Configure cost and usage alarms for COS, CDN, Cloud Infinite, retrieval, requests, storage, and application public bandwidth.

## 4. Implemented Foundation

### Storage and credentials

- Replaced MinIO runtime storage with Tencent COS and removed MinIO/local enablement branches from migrated paths.
- Added one lifecycle-managed COS client using CVM instance-role credentials.
- Added streaming and multipart backend uploads, reads, metadata inspection, deletion, and task-bounded presigned model URLs.
- Added COS diagnostics and metrics with credential/query redaction.

### Browser direct upload

- Added authenticated upload sessions and per-request COS authorization for `cos-js-sdk-v5`.
- The backend signs only the assigned object pathname, allowed method, allowed query keys, and allowed signable headers.
- The signer now requires the exact configured bucket host: `${bucket}.cos.${region}.myqcloud.com`.
- Create-object requests require the configured `INTELLIGENT_TIERING` storage class, and completion verifies the actual storage class returned by COS.
- Completion verifies staging object length, ETag, MIME type, and storage class with `HEAD Object`.
- A verified staging object is copied server-side to an immutable `materials/.../v1/original.ext` key with an ETag precondition. The business session stores only the final key. A still-valid old upload signature can therefore overwrite only an unreferenced staging object.
- The staging source is intentionally left for lifecycle cleanup so a database transaction failure does not destroy retryability.

### Delivery and media metadata

- Added immutable media-object/rendition metadata and seven-day delivery-grant persistence.
- Added deterministic Type D CDN URL derivation and separate COS presigned URLs for external AI providers.
- Updated frontend consumers to prefer display/thumbnail/cover delivery URLs rather than durable provider URLs.
- Regenerated the OpenAPI client; the meaningful generated diffs are `mediaUploadController.ts` and `typings.d.ts`. Other generated files may show only line-ending status and must not be hand-edited.

### Cloud Infinite foundation

- Added `ImageDisplayRenditionPlanner`, `MediaObjectRegistry`, processing-job persistence, callback token hashing, and callback correlation checks.
- Added fixed `imageSlim` rules and `imageMogr2/format/png|imageSlim` for unsupported formats.
- Terminal callback handling is now idempotent for both `SUCCEEDED` and `FAILED`; a later opposite terminal callback cannot rewrite the result.

## 5. Verification Evidence

Fresh verification completed immediately before this handoff:

```text
mvn -Dtest=ObjectStorageKeyFactoryTest,ObjectStorageServiceCosTest,MediaUploadSessionServiceTest test
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0

mvn -Dtest=CloudInfiniteProcessingServiceTest test
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0

mvn -DskipTests clean package
BUILD SUCCESS

npm run test
Test Files: 75 passed; Tests: 393 passed

npm run lint
Exit 0; 3 existing direct document.cookie warnings

npx antd lint ./src
Exit 0; 11 existing usage/deprecation warnings

npm run build
Webpack compiled successfully

git diff --check
Exit 0; line-ending warnings only
```

Full backend `mvn test` is currently blocked by a known branch-baseline authentication test mismatch:

- 1,169 tests ran before the run was stopped.
- 146 failures appeared across 25 controller/integration test classes.
- All observed failures expected HTTP 200 but received HTTP 400.
- Those tests still hard-code registration verification code `123456`, while the existing authentication implementation now generates random SMS verification codes.
- This COS branch did not modify that authentication behavior. Do not mark OpenSpec task `8.3` complete until the baseline suite is repaired or a project owner explicitly accepts a documented exception.

## 6. Known Blocking Gaps

### P0: Connect persistent `imageSlim` jobs to real write paths

`CloudInfiniteProcessingService.submitImageDisplay` and `MediaObjectRegistry.registerOriginal` currently have no production callers; code search finds only their definitions and tests. Real writes still use synchronous COS `PicOperations` in `ObjectStorageService`, and some domain services infer derived keys immediately.

The next implementation must register the verified original, create a pending display rendition, persist the processing job, wait for authenticated callback completion, and publish only after the rendition is ready. Cover these sources explicitly:

- Browser-uploaded images
- AI-generated images and saved variants
- Imported inspiration images
- Style-library images
- Reference/storyboard images
- Video cover images

Do not mark a rendition ready with `fileSize=0`, guessed metadata, or a guessed derived path.

### P0: Make browser multipart recovery survive caller-level failure

`startMediaUpload` creates the session internally and exposes pause/resume only after COS provides a task ID. On rejection, normal callers lose the session/task handle and a retry creates a new session. Refactor the public contract so callers can retain the upload session and COS task ID and invoke `restartTask` for the same object. Add a failing interruption/recovery test before implementation.

### P0: Remove the unsafe historical-path migration

`V127__image_slim_display_paths.sql` currently rewrites every `style_library.storage_path` to a `derived/display.*` path without creating or verifying the corresponding COS object. This would point historical rows at missing data and contradict the no-historical-backfill scope.

Replace it with a safe migration that preserves existing historical paths. Only a separate verified backfill may update a row after the actual derived object exists. Update all schema snapshot/rehearsal tests accordingly.

### P1: Correct the runbook output extension

`docs/tencent-cos-media-storage-runbook.md` still states that every image writes `display.webp`. The approved contract preserves JPEG/PNG/GIF output where supported and uses PNG after unsupported-format conversion. Remove the hard-coded WebP statement.

### P1: Re-audit OpenSpec completion markers

Tasks `6.1` through `6.4` and their downstream domain-integration tasks are marked complete, but the production-call search above proves the asynchronous persisted-job path is not wired end to end. Re-open any checkbox whose acceptance criteria are not demonstrably satisfied after the integration changes.

## 7. Recommended Continuation Order

1. Write red tests for original registration, pending rendition state, job persistence, and callback-gated publication in one representative image flow.
2. Implement one end-to-end asynchronous path and extract only the shared orchestration needed by the other image sources.
3. Extend the same contract to every source listed in section 6, with focused tests per domain.
4. Write a real frontend interruption/recovery test, then preserve and expose session/task state across a failed upload attempt.
5. Neutralize `V127__image_slim_display_paths.sql` and update migration snapshot tests.
6. Correct the runbook extension language.
7. Regenerate OpenAPI only with `npm run openapi`; never edit `frontend/src/services/ant-design-pro/` manually.
8. Run focused backend tests, frontend tests, lint, antd lint, frontend build, `mvn -DskipTests clean package`, and `git diff --check`.
9. Address or formally separate the existing registration-verification test baseline before running and claiming a clean full backend suite.
10. Finish Tencent Cloud console tasks and then execute OpenSpec integration, cache, range, cost, and rollback checks `8.4` through `8.9`.

## 8. Commands to Resume

```powershell
cd D:\需求文档\ant_short_TV\.worktrees\migrate-media-storage-to-tencent-cos
openspec status --change "migrate-media-storage-to-tencent-cos" --json
openspec instructions apply --change "migrate-media-storage-to-tencent-cos" --json
rg -n "submitImageDisplay|registerOriginal" backend/src/main backend/src/test
git status --short --branch
```

After changing backend code, start with focused tests and finish with:

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
- The callback URL base is configuration; each processing job appends a single-purpose random token whose hash is stored.
- The current broad COS/CI managed policies are accepted for initial readiness. Tightening to least privilege remains a follow-up and must not remove the exact upload, copy, metadata, CI job, callback-output, and delivery operations used by the application.
