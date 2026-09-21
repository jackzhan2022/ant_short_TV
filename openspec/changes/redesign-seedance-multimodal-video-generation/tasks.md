## 1. Schema And Model Catalogue

- [x] 1.1 Add failing schema migration tests for the mini Model, preserved existing Model IDs, constraint profiles, project video defaults, video task snapshot fields, `ai_video_task_reference`, and version 1 prompt cleanup.
- [x] 1.2 Add the next additive Flyway migration to update the three existing Seedance definitions in place, add Seedance 2.0 mini and its capability, store all four constraint profiles, and preserve historical pricing/task/log relationships.
- [x] 1.3 Extend the migration with project video-default columns, task snapshot/result-metadata columns, the normalized reference table and indexes, and destructive clearing of version 1 `prompt_document_json` while retaining `video_prompt`.
- [x] 1.4 Update schema version expectations and run the focused schema migration tests through both fresh-schema and upgrade paths.

## 2. Platform Configuration And Project Defaults

- [x] 2.1 Add backend tests for authorized Endpoint-ID editing, placeholder/blank enablement rejection, tenant-safe Model options, constraint-profile exposure, and mini default routing.
- [x] 2.2 Extend platform Model management contracts and validation so authorized administrators can save Seedance Endpoint IDs while tenant APIs expose only enabled Models and safe constraint metadata.
- [x] 2.3 Add backend project API tests for resolution, generated-audio, and watermark defaults on create, update, detail, and legacy requests that omit the new fields.
- [x] 2.4 Implement project persistence and API mapping for `720p`, generated audio enabled, and watermark disabled defaults while retaining project aspect ratio as the video ratio source.

## 3. Version 2 Prompt Compilation And Media Snapshots

- [x] 3.1 Add prompt-document validation tests that accept version 2 typed image/video/audio Mentions and reject every version 1 save request.
- [x] 3.2 Update backend and frontend prompt-document contracts to version 2 with stable source identity, media type, optional visual variant, and display name.
- [x] 3.3 Add compiler tests for independent `图片N`/`视频N`/`音频N` numbering, first-mention ordering, duplicate-source reuse, plain-text preservation, and mixed media.
- [x] 3.4 Implement a backend prompt compiler that resolves Mentions within tenant/project scope and produces compiled text plus ordered unique reference commands.
- [x] 3.5 Add authorization and resolution tests for missing, deleted, cross-tenant, cross-project, and changed material sources.
- [x] 3.6 Implement trusted image, video, and audio resolution through existing project material and visual-variant data, including object-storage reuse or upload/copy for provider-accessible URLs.
- [x] 3.7 Add model-boundary tests for output duration/resolution and every supplied image, video, and audio format, size, dimension, ratio, pixel, FPS, count, and total-duration rule.
- [x] 3.8 Implement model-driven multimodal validation that fails with material-specific diagnostics before execution creation, point reservation, or provider contact.
- [x] 3.9 Add persistence tests and implement immutable task/reference snapshots so technical retries never re-read edited prompts or replaced materials.

## 4. Ark Submission, Polling, And Cancellation

- [x] 4.1 Replace the current adapter request tests with exact multimodal POST assertions for text, ordered role-bearing media, Endpoint ID, generated audio, ratio, duration, resolution, watermark, authorization, and idempotency.
- [x] 4.2 Refactor the Seedance Ark adapter to submit the frozen multimodal request and keep provider transport separate from prompt compilation and material ownership logic.
- [x] 4.3 Add polling tests for running, succeeded, failed, expired, canceled, unknown non-terminal, top-level error, and nested error responses.
- [x] 4.4 Extend polling response mapping and task completion persistence for video URL, actual duration, resolution, ratio, seed, FPS, service tier, generated-audio flag, usage tokens, and bounded sanitized diagnostics.
- [x] 4.5 Add cancellation tests for HTTP 200 with empty `Result`, repeated cancellation, missing/already-terminal remote tasks, provider failure, completion races, and post-cancel polling fences.
- [x] 4.6 Implement provider-native DELETE cancellation and integrate its idempotent outcome with local execution, reservation release, attempt history, and scheduler eligibility.
- [x] 4.7 Run the focused backend video controller, service, router, adapter, accounting, and migration test suites and fix regressions without weakening historical execution guarantees.

## 5. Workbench And Project UI

- [x] 5.1 Run `npx antd info` for every Ant Design component introduced or materially changed in the project settings, material selector, and generation parameter controls.
- [x] 5.2 Add frontend tests for project creation/editing defaults and replace fixed resolution metadata with authoritative project values.
- [x] 5.3 Implement project resolution, generated-audio, and watermark controls with the confirmed defaults and preserve existing creation callers.
- [x] 5.4 Add prompt-editor tests for image/video/audio material selection, version 2 Mention insertion/deletion, display-name rendering, stable node ordering, and version 1 rejection.
- [x] 5.5 Make the existing `+` and `@` controls open a project-scoped categorized material selector while preserving the current storyboard-card and Mention appearance.
- [x] 5.6 Add generation-control tests for enabled Model loading, real `modelId` submission, project-default inheritance, per-task overrides, intelligent duration, and visible `720p` fallback after an incompatible Model switch.
- [x] 5.7 Replace the hard-coded Model and task parameters with backend Model constraints and a compact settings control that submits effective duration, resolution, generated-audio, watermark, and project aspect ratio.
- [x] 5.8 Add batch-generation tests and implement independent snapshot/submission outcomes so an invalid storyboard reports its own failure without blocking valid siblings.

## 6. Verification And Rollout

- [x] 6.1 Regenerate affected frontend service contracts through the repository OpenAPI workflow when generated APIs change, without editing generated service files manually.
- [x] 6.2 Run focused frontend tests, focused backend tests, schema migration tests, frontend type checking, `npm run lint`, `npx antd lint ./src`, backend tests, and the production build; resolve all failures.
- [x] 6.3 Verify an upgraded database preserves historical Models, tasks, results, pricing, accounting, and logs while clearing only version 1 prompt JSON and retaining plain prompts.
- [x] 6.4 Configure the four Endpoint IDs and Provider credentials through platform management, enable all four Models, set mini as default, and verify tenant Model options do not expose credentials or Endpoint IDs.
- [ ] 6.5 Run real Ark smoke tests for multimodal submit, polling, project-owned result download, remote cancellation, failure diagnostics, and deterministic retry without logging secrets or signed URL queries.
- [x] 6.6 Update the video-generation operations/runbook documentation with configuration, validation, version 1 cleanup, smoke-test, rollback, and recovery procedures.
