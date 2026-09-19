# Batch Asset Image Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add current-tab asset selection, consolidated extraction actions, and persistent batch generation of selected assets' primary or all visual variants.

**Architecture:** Persist batches and variant-level items in the backend. A scheduler submits ready items through the existing image task service and releases character-dependent items only after their primary task succeeds; the frontend performs preflight, confirmation, submission, and polling.

**Tech Stack:** Spring Boot 3, MyBatis Plus/JdbcTemplate, Flyway, React 19, Ant Design 6, TypeScript, Vitest, JUnit 5

---

### Task 1: Batch schema and API contracts

**Files:**
- Create: `backend/src/main/resources/db/migration/V118__asset_image_batch.sql`
- Create: `backend/src/main/java/com/antshorttv/aiimage/AssetImageBatchModels.java`
- Create: `backend/src/main/java/com/antshorttv/aiimage/AssetImageBatchController.java`
- Test: `backend/src/test/java/com/antshorttv/aiimage/AssetImageBatchControllerTest.java`

- [x] Write controller tests for preflight, create, get, validation, permissions, tenant/project scoping, and idempotency headers.
- [x] Run the controller test and verify RED because contracts do not exist.
- [x] Add batch and batch-item tables plus request/response records and controller routes.
- [x] Run the controller test and verify contract GREEN using a mocked service.

### Task 2: Preflight planner

**Files:**
- Create: `backend/src/main/java/com/antshorttv/aiimage/AssetImageBatchService.java`
- Modify: `backend/src/main/java/com/antshorttv/script/AssetVisualVariantService.java`
- Test: `backend/src/test/java/com/antshorttv/aiimage/AssetImageBatchServiceTest.java`

- [x] Write failing tests for empty selection, cross-project IDs, `PRIMARY`/`ALL`, missing default primary, completed/generating skips, failed/not-started eligibility, missing prompts, and character dependency counts.
- [x] Implement a read-only plan builder. Treat an asset without variants as a hypothetical default primary during preflight.
- [x] Add a narrowly scoped variant helper used at submission time to create a missing “默认形象” primary with the canonical asset prompt.
- [x] Verify all preflight tests pass.

### Task 3: Persistent submission and scheduler

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/aiimage/AssetImageBatchService.java`
- Create: `backend/src/main/java/com/antshorttv/aiimage/AssetImageBatchScheduler.java`
- Modify: `backend/src/main/java/com/antshorttv/aiimage/AiImageTaskService.java`
- Test: `backend/src/test/java/com/antshorttv/aiimage/AssetImageBatchExecutionTest.java`

- [x] Write failing tests for idempotent batch creation, direct item dispatch, unique per-item image-task idempotency, primary-success dependency release, primary-failure dependency skip, partial failures, terminal batch status, and persisted progress after service recreation.
- [x] Add an explicit-key internal image-task creation entry point that still reuses validation, model resolution, point reservation, logging, and visual-variant ownership.
- [x] Persist planned items with `PENDING`, `WAITING_DEPENDENCY`, or `SKIPPED` status.
- [x] Implement a scheduled dispatcher that refreshes task-backed items, releases dependencies, and dispatches ready items independently of the frontend.
- [x] Verify execution tests and existing `AiImageTaskControllerTest` pass.

### Task 4: Frontend service contracts

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/service.ts`
- Test: `frontend/src/pages/projects/production-workbench/service.test.ts`

- [x] Write failing tests for preflight, submit with `Idempotency-Key`, and batch detail requests.
- [x] Add TypeScript request/response types and the three service functions.
- [x] Verify service tests pass.

### Task 5: Selection and consolidated toolbar

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/settings.test.tsx`

- [x] Write failing tests for card checkboxes, current-tab select-all, selection count, clearing on tab change, disabled image-generation button, consolidated extraction menu, and both image-generation menu choices.
- [x] Add controlled card selection and clear it whenever `activeType` changes.
- [x] Replace the two extraction buttons with one `Dropdown` menu.
- [x] Add the disabled-until-selected asset-image `Dropdown` menu.
- [x] Verify the focused settings tests pass.

### Task 6: Preflight confirmation and submission

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/settings.test.tsx`

- [x] Write failing tests for preflight request data, model loading, default `16:9`, `1/2/4` image counts, confirmation summaries, total image count, submit payload, clearing selection, and no-eligible-item handling.
- [x] Implement the confirmation modal with model, ratio, and count controls.
- [x] Change single-image generation initialization so characters also default to `16:9`.
- [x] Submit with a stable generated idempotency key and clear selection only after success.
- [x] Verify focused modal and single-generation regression tests pass.

### Task 7: Batch tracking and final verification

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/settings.test.tsx`

- [x] Write failing tests for two-second polling, asset-summary refresh, terminal success/partial-failure/failure messages, timer cleanup, and stale-project response protection.
- [x] Implement active-batch polling and scoped workspace reload.
- [x] Run affected backend tests: `mvn -f backend/pom.xml -Dtest=AssetImageBatchControllerTest,AssetImageBatchServiceTest,AssetImageBatchExecutionTest,AiImageTaskControllerTest test`.
- [x] Run affected frontend tests: `npm --prefix frontend test -- src/pages/projects/production-workbench/service.test.ts src/pages/projects/production-workbench/settings.test.tsx`.
- [x] Run `npm --prefix frontend run tsc`, `npm --prefix frontend run biome:lint`, and `npx --prefix frontend antd lint ./frontend/src`.
- [x] Commit with `feat(settings): add batch asset image generation`.
