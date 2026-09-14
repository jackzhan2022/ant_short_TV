## 1. Lock the Fast-Creation Contract with Tests

- [x] 1.1 Update `VideoDecompositionControllerTest.createsUnboundBatchAndKeepsUploadOrderAsEpisodeNumbers` to submit multiple videos and assert that ordered episodes plus initial pending attempts are persisted while every returned `executionId` is null and no `ai_execution_task` or `ai_point_reservation` exists for those episode business IDs.
- [x] 1.2 Run the focused controller test and confirm it fails because batch creation still eagerly assigns execution IDs and creates reservations.
- [x] 1.3 Review controller retry fixtures that read an execution ID immediately after batch creation and isolate their explicit frozen-execution setup from the new batch-creation contract.

## 2. Defer Execution and Billing Initialization

- [x] 2.1 Remove the eager `createExecutionHeader` call from the per-video loop in `VideoDecompositionService.create` while retaining atomic batch, ordered episode, and initial `VIDEO_ANALYSIS/PENDING` attempt persistence.
- [x] 2.2 Remove the now-unused private eager execution-header method and only the imports or direct dependencies that are no longer used elsewhere in `VideoDecompositionService`.
- [x] 2.3 Run the focused controller test and confirm the batch returns with null execution IDs, no synchronous reservations, and all ordered pending attempts present.

## 3. Verify Lazy Background Initialization

- [x] 3.1 Extend `VideoDecompositionExecutionServiceTest` to start from a claimed or pending episode with a null `execution_id` and an existing initial attempt, then assert that execution creates exactly one execution task and point reservation before provider contact and writes the execution ID back to the episode.
- [x] 3.2 Add or update the failure-path test so missing billing or insufficient points records an episode-level recoverable/failed outcome while the batch and sibling episode rows remain present.
- [x] 3.3 Run `VideoDecompositionExecutionServiceTest` and `VideoDecompositionTaskSchedulerTest` and confirm lazy initialization, atomic claim behavior, and failure recovery pass.

## 4. Regression Verification

- [x] 4.1 Update existing video decomposition controller retry tests to create their required frozen execution explicitly and verify technical retry still reuses its execution version, billing versions, and reservation.
- [x] 4.2 Run the complete video decomposition test set covering controller, execution service, scheduler, progress, retry policy, direct screenplay normalization, and script-result persistence.
- [x] 4.3 Run backend compilation plus the full backend test suite and confirm no other workflow assumes every newly persisted pending episode already has an execution ID.
- [x] 4.4 Run `openspec validate async-video-batch-creation --strict` and confirm the proposal, delta specs, design, and tasks remain valid.
