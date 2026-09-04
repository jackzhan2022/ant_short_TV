# Storyboard Source Segments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fragile copied source markers and serial model-selected reads with fingerprint-bound source segment ranges and one prepared storyboard planning request.

**Architecture:** A deterministic `EpisodeSourceSegmenter` assigns `S0001` IDs and trusted offsets to relevant physical lines. `WorkflowAgentRunner` host-executes and audits the five storyboard read tools, supplies one combined context to the model, and exposes only the terminal save tool. The save service resolves segment ranges and spoken-segment references against trusted run state before its existing atomic replacement. Deterministic validation is non-retryable at execution-attempt level; provider transport failures retain bounded retry.

**Tech Stack:** Java 21, Spring Boot, Jackson, JDBC/MyBatis-Plus, JUnit 5, Mockito, AssertJ, Maven, OpenSpec.

---

### Task 1: Deterministic episode source segmentation

**Files:**
- Create: `backend/src/main/java/com/antshorttv/workflowagent/tool/EpisodeSourceSegmenter.java`
- Create: `backend/src/test/java/com/antshorttv/workflowagent/tool/EpisodeSourceSegmenterTest.java`

- [ ] **Step 1: Write failing segmentation tests**

Cover CRLF/LF normalization without changing segment text, blank-line omission, episode-title metadata with `requiredCoverage=false`, scene/action/dialogue/narration/inner-OS classification, exact source offsets, and stable `S0001` ordering. Use a concrete fixture:

```java
String source = "第1集：门缝里的阴谋\r\n\r\n场景：夜 内 走廊\r\n△ Serena停下。\r\nSerena：谁在那里？";
List<EpisodeSourceSegment> segments = segmenter.segment(source);
assertThat(segments).extracting(EpisodeSourceSegment::id)
    .containsExactly("S0001", "S0002", "S0003", "S0004");
assertThat(segments.get(0).requiredCoverage()).isFalse();
assertThat(segments.get(3).type()).isEqualTo(SourceSegmentType.DIALOGUE);
assertThat(source.substring(segments.get(3).startOffset(), segments.get(3).endOffset()))
    .isEqualTo("Serena：谁在那里？");
```

- [ ] **Step 2: Run the new test and verify RED**

Run: `mvn -f backend/pom.xml -Dtest=EpisodeSourceSegmenterTest test`

Expected: compilation failure because `EpisodeSourceSegmenter` and its value types do not exist.

- [ ] **Step 3: Implement the minimal segmenter**

Create an immutable nested record or package-local records with:

```java
record EpisodeSourceSegment(
    String id,
    SourceSegmentType type,
    String text,
    int startOffset,
    int endOffset,
    boolean requiredCoverage
) {}
```

Scan physical lines while retaining original offsets, skip blank lines, assign ordinal IDs, classify explicit scene headings and spoken prefixes, and mark episode/document headings as context-only. Do not split a non-blank physical line.

- [ ] **Step 4: Run the test and verify GREEN**

Run: `mvn -f backend/pom.xml -Dtest=EpisodeSourceSegmenterTest test`

Expected: all segmentation tests pass.

- [ ] **Step 5: Commit the isolated segmenter**

```bash
git add backend/src/main/java/com/antshorttv/workflowagent/tool/EpisodeSourceSegmenter.java backend/src/test/java/com/antshorttv/workflowagent/tool/EpisodeSourceSegmenterTest.java
git commit -m "feat(storyboard): segment trusted episode source"
```

### Task 2: Expose and retain trusted segments during the current-episode read

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolConfiguration.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataServiceTest.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolCatalogTest.java`

- [ ] **Step 1: Write failing read-output tests**

Assert `read_current_episode` returns `sourceSegments`, does not expose offsets, includes exact `id/type/text/requiredCoverage`, and stores the complete segment list in `WorkflowToolRunState` under `currentEpisodeSourceSegments`.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -f backend/pom.xml -Dtest=ScreenplayToolDataServiceTest,ScreenplayToolCatalogTest test`

Expected: assertions fail because the read output schema and payload do not contain `sourceSegments`.

- [ ] **Step 3: Inject and use the segmenter**

In `readCurrentEpisode`, compute segments once, store the immutable list in run state, and serialize only:

```json
{"id":"S0001","type":"SCENE","text":"场景：夜 内 走廊","requiredCoverage":true}
```

Keep the current `content` field for compatibility during this change, but tell the storyboard Skill that segment IDs are authoritative.

- [ ] **Step 4: Extend the tool output schema and verify GREEN**

Run: `mvn -f backend/pom.xml -Dtest=ScreenplayToolDataServiceTest,ScreenplayToolCatalogTest test`

Expected: focused tests pass.

- [ ] **Step 5: Commit trusted read integration**

```bash
git add backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolConfiguration.java backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataServiceTest.java backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolCatalogTest.java
git commit -m "feat(storyboard): expose trusted source segments"
```

### Task 3: Replace copied markers with segment ranges in the save schema

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolConfiguration.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/tool/StoryboardToolSchemaTest.java`

- [ ] **Step 1: Write the failing schema contract**

Require `sourceFrom`, `sourceTo`, and per-shot `soundSegmentIds`; reject legacy `sourceStartMarker`, `sourceEndMarker`, `dialogue`, `narration`, and `innerOs` because the schema uses `additionalProperties=false`.

```java
assertThat(board.path("required")).contains("sourceFrom", "sourceTo");
assertThat(shot.path("required")).contains("soundSegmentIds");
assertThat(board.path("properties").has("sourceStartMarker")).isFalse();
```

- [ ] **Step 2: Run the schema test and verify RED**

Run: `mvn -f backend/pom.xml -Dtest=StoryboardToolSchemaTest test`

Expected: schema still exposes legacy marker and copied sound fields.

- [ ] **Step 3: Implement schema version 2**

Set `schemaVersion` minimum and maximum to `2`. Define `sourceFrom/sourceTo` with pattern `^S\\d{4,}$`. Define `soundSegmentIds` as a required, possibly empty, unique string array with the same pattern and a bounded item count.

- [ ] **Step 4: Run schema tests and verify GREEN**

Run: `mvn -f backend/pom.xml -Dtest=StoryboardToolSchemaTest test`

Expected: schema contract passes.

- [ ] **Step 5: Commit schema v2**

```bash
git add backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolConfiguration.java backend/src/test/java/com/antshorttv/workflowagent/tool/StoryboardToolSchemaTest.java
git commit -m "feat(storyboard): accept source segment ranges"
```

### Task 4: Validate coverage and inject authoritative spoken text

**Files:**
- Create: `backend/src/main/java/com/antshorttv/workflowagent/tool/WorkflowToolValidationException.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/StoryboardToolDataService.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/tool/StoryboardToolDataServiceTest.java`

- [ ] **Step 1: Write failing persistence tests**

Add independent tests proving:

- `S0001..S0004` split into two adjacent ranges saves;
- an unknown segment returns `SOURCE_SEGMENT_UNKNOWN`;
- `S0002` followed by `S0004` returns `SOURCE_SEGMENT_GAP` with expected and actual IDs;
- overlap and reversed ranges fail;
- stale fingerprint fails before mutation;
- duplicate/missing `soundSegmentIds` fail;
- dialogue/narration/inner OS stored in the validated shot plan comes from trusted segment text, not model text;
- every invalid case leaves existing active storyboards unchanged.

- [ ] **Step 2: Run the data-service test and verify RED**

Run: `mvn -f backend/pom.xml -Dtest=StoryboardToolDataServiceTest test`

Expected: new payloads fail because the service still resolves copied marker strings.

- [ ] **Step 3: Add structured validation diagnostics**

Implement `WorkflowToolValidationException` as a `BusinessException` carrying an immutable details map. Provide concrete diagnostic fields `validationCode`, `storyboardNo`, `expectedSegmentId`, and `actualSegmentId` when applicable.

- [ ] **Step 4: Replace marker resolution**

Load `currentEpisodeSourceSegments` from trusted run state, map IDs to ordinals, require adjacent complete ranges across `requiredCoverage=true` segments, resolve trusted source slices by offsets, and resolve each `soundSegmentIds` entry by segment type. Remove `uniqueIndex` from the storyboard save path.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run: `mvn -f backend/pom.xml -Dtest=StoryboardToolDataServiceTest test`

Expected: all valid, invalid, rollback, and spoken-text tests pass.

- [ ] **Step 6: Commit save validation**

```bash
git add backend/src/main/java/com/antshorttv/workflowagent/tool/WorkflowToolValidationException.java backend/src/main/java/com/antshorttv/workflowagent/tool/StoryboardToolDataService.java backend/src/test/java/com/antshorttv/workflowagent/tool/StoryboardToolDataServiceTest.java
git commit -m "feat(storyboard): validate source segment coverage"
```

### Task 5: Host-drive the five reads and make one planning request

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunContract.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/run/StoryboardAgentRunContractTest.java`

- [ ] **Step 1: Write failing runner tests**

Assert a storyboard Run:

- executes all five read tool executors before `invokeText`;
- records five successful TOOL steps in order;
- calls the model once for a first-pass successful save;
- sends one user message containing all five serialized read results;
- exposes only `save_episode_storyboards` to the provider;
- never includes old storyboard data;
- refuses to invoke the model if a host read fails.

- [ ] **Step 2: Run runner tests and verify RED**

Run: `mvn -f backend/pom.xml -Dtest=WorkflowAgentRunnerTest,StoryboardAgentRunContractTest test`

Expected: provider is currently invoked before each read and the first provider tool is `read_current_episode`.

- [ ] **Step 3: Implement storyboard prepared context**

Add a storyboard-only preparation helper inside `WorkflowAgentRunner` that uses the existing registry, schema validator, scope guard, context, contract, and `runs.recordToolStep`. Build one bounded JSON object keyed by tool code, append it as the planning user message, and start the regular loop with the correct consumed step count and successful tool state.

- [ ] **Step 4: Limit provider tools to terminal save**

Change the storyboard branch of `activeProviderTools` so only `save_episode_storyboards` is exposed after preparation. Keep other Agent behavior unchanged.

- [ ] **Step 5: Run runner and contract tests and verify GREEN**

Run: `mvn -f backend/pom.xml -Dtest=WorkflowAgentRunnerTest,StoryboardAgentRunContractTest test`

Expected: tests pass and a successful storyboard Run contains five host TOOL steps, one MODEL step, and one save TOOL step.

- [ ] **Step 6: Commit runner orchestration**

```bash
git add backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunContract.java backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java backend/src/test/java/com/antshorttv/workflowagent/run/StoryboardAgentRunContractTest.java
git commit -m "feat(storyboard): prepare agent context in host"
```

### Task 6: Target one correction and stop deterministic full retries

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java`
- Modify: `backend/src/main/java/com/antshorttv/execution/AiExecutionHandler.java`
- Modify: `backend/src/main/java/com/antshorttv/execution/AiExecutionWorker.java`
- Create: `backend/src/main/java/com/antshorttv/script/NonRetryableStoryboardException.java`
- Modify: `backend/src/main/java/com/antshorttv/script/StoryboardAgentAdapter.java`
- Modify: `backend/src/main/java/com/antshorttv/script/ScriptAiOperationExecutionHandler.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java`
- Modify: `backend/src/test/java/com/antshorttv/execution/AiExecutionWorkerTest.java`
- Modify: `backend/src/test/java/com/antshorttv/script/ScriptAiOperationExecutionHandlerTest.java`

- [ ] **Step 1: Write failing correction and retry tests**

Prove one structured save failure is returned to the same model history, a corrected second save may succeed, and the same `validationCode` twice terminates the Run. Prove a deterministic storyboard exception selects `AiExecutionRetryPolicy.none()` while `AiGatewayException` retains three attempts with five-second delay.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -f backend/pom.xml -Dtest=WorkflowAgentRunnerTest,AiExecutionWorkerTest,ScriptAiOperationExecutionHandlerTest test`

Expected: runner permits repeated correction until step limit and worker always uses the handler's static retry policy.

- [ ] **Step 3: Serialize structured details to the model**

Make `writeError` merge `WorkflowToolValidationException.details()` into the returned tool-result JSON. Track the first storyboard validation code in the Run; allow one correction and throw on repetition or a second different deterministic validation failure.

- [ ] **Step 4: Select retry policy by failure**

Add a backwards-compatible `AiExecutionHandler.retryPolicy(Throwable failure)` default method delegating to `retryPolicy()`. Make `AiExecutionWorker` call it. Wrap deterministic storyboard save failures in `NonRetryableStoryboardException`, and make `ScriptAiOperationExecutionHandler` return `none()` for that marker while preserving its existing policy for provider/transport failures.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run: `mvn -f backend/pom.xml -Dtest=WorkflowAgentRunnerTest,AiExecutionWorkerTest,ScriptAiOperationExecutionHandlerTest test`

Expected: correction and retry classification tests pass.

- [ ] **Step 6: Commit retry behavior**

```bash
git add backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java backend/src/main/java/com/antshorttv/execution/AiExecutionHandler.java backend/src/main/java/com/antshorttv/execution/AiExecutionWorker.java backend/src/main/java/com/antshorttv/script/NonRetryableStoryboardException.java backend/src/main/java/com/antshorttv/script/StoryboardAgentAdapter.java backend/src/main/java/com/antshorttv/script/ScriptAiOperationExecutionHandler.java backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java backend/src/test/java/com/antshorttv/execution/AiExecutionWorkerTest.java backend/src/test/java/com/antshorttv/script/ScriptAiOperationExecutionHandlerTest.java
git commit -m "fix(storyboard): avoid deterministic full retries"
```

### Task 7: Update Agent instructions and integration fixtures

**Files:**
- Modify: `backend/skills/short-drama-storyboard-planning/SKILL.md`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/agent/StoryboardAgentBootstrap.java`
- Modify: `backend/src/main/java/com/antshorttv/script/StoryboardAgentAdapter.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/skill/StoryboardSkillContractTest.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/agent/StoryboardAgentBootstrapTest.java`
- Modify: `backend/src/test/java/com/antshorttv/script/StoryboardAgentAdapterTest.java`

- [ ] **Step 1: Write failing instruction tests**

Require instructions to mention `sourceFrom`, `sourceTo`, `soundSegmentIds`, schema version 2, complete segment coverage, and one prepared context. Assert they no longer tell the model to copy marker text or call read tools itself.

- [ ] **Step 2: Run instruction tests and verify RED**

Run: `mvn -f backend/pom.xml -Dtest=StoryboardSkillContractTest,StoryboardAgentBootstrapTest,StoryboardAgentAdapterTest test`

Expected: legacy marker and read-sequence instructions fail the new assertions.

- [ ] **Step 3: Update only storyboard-facing instructions**

Describe contiguous segment ranges, authoritative server-injected speech, and the terminal save contract. Keep material, duration, visual elaboration, prompt rendering, and episode scope rules unchanged.

- [ ] **Step 4: Run instruction tests and verify GREEN**

Run: `mvn -f backend/pom.xml -Dtest=StoryboardSkillContractTest,StoryboardAgentBootstrapTest,StoryboardAgentAdapterTest test`

Expected: focused instruction tests pass.

- [ ] **Step 5: Commit instruction updates**

```bash
git add backend/skills/short-drama-storyboard-planning/SKILL.md backend/src/main/java/com/antshorttv/workflowagent/agent/StoryboardAgentBootstrap.java backend/src/main/java/com/antshorttv/script/StoryboardAgentAdapter.java backend/src/test/java/com/antshorttv/workflowagent/skill/StoryboardSkillContractTest.java backend/src/test/java/com/antshorttv/workflowagent/agent/StoryboardAgentBootstrapTest.java backend/src/test/java/com/antshorttv/script/StoryboardAgentAdapterTest.java
git commit -m "docs(storyboard): instruct segment based planning"
```

### Task 8: Complete verification, OpenSpec tracking, deployment, and live smoke

**Files:**
- Modify: `openspec/changes/add-storyboard-workflow-agent/tasks.md`
- Modify only if verification finds a contract mismatch: `openspec/changes/add-storyboard-workflow-agent/design.md`

- [ ] **Step 1: Run the complete focused storyboard suite**

Run:

```bash
mvn -f backend/pom.xml -Dtest=EpisodeSourceSegmenterTest,ScreenplayToolDataServiceTest,ScreenplayToolCatalogTest,StoryboardToolSchemaTest,StoryboardToolDataServiceTest,WorkflowAgentRunnerTest,StoryboardAgentRunContractTest,StoryboardSkillContractTest,StoryboardAgentBootstrapTest,StoryboardAgentAdapterTest,ScriptAiOperationExecutionHandlerTest,AiExecutionWorkerTest test
```

Expected: zero failures and zero errors.

- [ ] **Step 2: Run complete backend verification**

Run: `mvn -f backend/pom.xml test`

Expected: zero failures and zero errors.

- [ ] **Step 3: Run frontend regression verification**

Run from `frontend`:

```bash
npm test -- --runInBand
npm run tsc
npm run lint
npx antd lint ./src
npm run build
```

Expected: all tests, checks, and production build pass.

- [ ] **Step 4: Validate OpenSpec and update completed tasks**

Run: `openspec validate add-storyboard-workflow-agent --strict`.

Mark tasks 9.1 through 9.5 complete only after their evidence exists. Keep 9.6 and existing live-smoke task 8.4 open until production verification succeeds.

- [ ] **Step 5: Commit verification tracking and push**

```bash
git add openspec/changes/add-storyboard-workflow-agent/tasks.md
git commit -m "docs(storyboard): record segment workflow verification"
git push origin master
```

- [ ] **Step 6: Deploy with the existing runbook**

Build immutable backend/frontend artifacts, create a new timestamped release, back up database/environment/workflow Skills, verify local and remote SHA-256 values, update the `current` symlink, restart `antv.service`, and verify service, schema, feature flag, authenticated API, and public HTTP health. Preserve the previous release path for rollback.

- [ ] **Step 7: Run one production generation**

Use project 26 and its active episode 1. Confirm the page loads the real 58-episode project before clicking. Verify the execution reaches a terminal success, generated storyboards appear, each prompt contains visual style/material references/shot descriptions/fixed constraints, and Mention metadata is present.

- [ ] **Step 8: Verify production audit and accounting**

Read production tables without exposing credentials. Confirm one Agent Run has five audited host read steps, normally one model step, one successful save step, complete new storyboard rows owned by that Run, AI call/usage rows, and settled/released points consistent with the actual provider-call count.

- [ ] **Step 9: Verify failed regeneration preservation safely**

Exercise a deterministic invalid segment payload through an authenticated non-production test path or existing integration harness against a pre-existing storyboard set; do not mutate production Agent configuration merely to force failure. Confirm active storyboard IDs/content remain unchanged and the structured diagnostic is recorded.

- [ ] **Step 10: Close OpenSpec verification tasks**

Mark 8.4, 8.5, and 9.6 complete only when their live evidence exists, validate OpenSpec again, commit, push, and report the release path, commit, backup, rollback target, execution ID, Run ID, elapsed time, provider-call count, and settlement outcome.
