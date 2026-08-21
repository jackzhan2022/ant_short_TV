## 1. Data Model and Configuration

- [ ] 1.1 Add Flyway migration for decomposition batches, episode tasks, analysis results, execution attempts, statuses, indexes, and project/tenant ownership.
- [ ] 1.2 Add MyBatis entities and mappers for batches, episode tasks, analysis results, and execution attempts with project-scoped query helpers.
- [ ] 1.3 Extend AI model capability/configuration validation so an enabled Alibaba Bailian `qwen3.7-plus` model can be selected for video understanding.
- [ ] 1.4 Add backend request and response DTOs for batch creation, video upload metadata, batch detail, episode detail, retry, draft editing, and confirmation.

## 2. Video Storage and Qwen Integration

- [ ] 2.1 Add project-scoped video upload handling with format, size, duration, ordering, and ownership validation.
- [ ] 2.2 Add controlled model-accessible URL resolution and validation for object-stored video files without exposing provider credentials.
- [ ] 2.3 Implement the Qwen video-understanding adapter for Alibaba Bailian multimodal requests containing a video URL and a strict structured-response prompt.
- [ ] 2.4 Implement response normalization and schema validation for characters, scenes, props, timeline events, dialogue, actions, and emotions.
- [ ] 2.5 Record provider request ID, duration, token/cost fields where supplied, raw-response summary, and business parsing failures in `ai_call_log`.
- [ ] 2.6 Add provider error, timeout, rate-limit, retryability, and non-JSON response handling without treating transport success as decomposition success.

## 3. Decomposition Workflow

- [ ] 3.1 Implement batch creation that assigns stable episode numbers from the submitted upload order and enqueues independent episode tasks.
- [ ] 3.2 Implement asynchronous, concurrency-controlled episode analysis execution and batch progress aggregation.
- [ ] 3.3 Implement idempotent single-episode retry and regeneration behavior that preserves successful sibling episodes.
- [ ] 3.4 Implement screenplay-draft generation from the latest valid normalized analysis, with a separate AI call log entry and pending-review state.
- [ ] 3.5 Implement episode analysis/draft retrieval APIs, including raw response visibility restricted to authorized project users.
- [ ] 3.6 Implement review editing and explicit confirmation that imports an episode as a `VIDEO_IMPORT` script version without silently overwriting the current script.
- [ ] 3.7 Add optimistic version-conflict handling for confirmation and preserve the draft when confirmation fails.

## 4. Project UI

- [ ] 4.1 Add a project-level video decomposition entry point with multi-file upload, upload ordering, validation feedback, and model selection visibility.
- [ ] 4.2 Add batch progress and episode list views with analysis, draft-generation, review, confirmed, and failed states.
- [ ] 4.3 Add episode detail UI for structured analysis, raw response diagnostics, screenplay draft editing, retry, regenerate, and confirmation actions.
- [ ] 4.4 Connect confirmed episode drafts to the existing screenplay version experience while preserving episode numbers and source labels.
- [ ] 4.5 Add frontend request helpers, types, loading/error states, and permission-aware action visibility without editing generated service files.

## 5. Verification and Rollout

- [ ] 5.1 Add backend unit and controller tests for batch creation, validation, URL failures, structured parsing, provider failures, retries, progress, confirmation, and version conflicts.
- [ ] 5.2 Add adapter integration tests using a local HTTP server for Qwen request formatting, response parsing, request IDs, and error handling.
- [ ] 5.3 Add frontend tests for ordered uploads, progress rendering, failed-episode retry, draft review, and explicit confirmation flows.
- [ ] 5.4 Run Flyway/schema tests, backend test suite, frontend type/lint/tests, and targeted manual verification with a configured Alibaba Bailian test model.
- [ ] 5.5 Document provider setup, required object-storage URL access, supported video limits, status meanings, and operational troubleshooting for parsing failures.
