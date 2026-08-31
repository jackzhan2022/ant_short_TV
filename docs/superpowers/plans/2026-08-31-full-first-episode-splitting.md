# Full-First Episode Splitting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the episode-splitting Agent submit boundary-only results from a complete script in non-thinking mode when possible, then automatically and safely fall back to persisted structure-aware chunk analysis for capacity or incomplete-call failures.

**Architecture:** Keep `save_episode_splitting` and `EpisodeSplitBoundaryResolver` as the only formal write path. Add provider-level thinking control, a split-specific run policy layered onto `WorkflowAgentRunner`, persisted fallback snapshots/chunks, a deterministic chunk planner, and an internal chunk analyzer whose compact candidates are aggregated in a clean model context. Expose fallback mode and chunk progress through the existing single splitting stage.

**Tech Stack:** Java 17, Spring Boot 3.3, JDBC/MyBatis-Plus, Flyway/MySQL, Jackson, JUnit 5/AssertJ/Mockito, React 19, Umi Max, TypeScript, Ant Design 6, Vitest.

---

## File map

- Modify `backend/src/main/java/com/antshorttv/ai/AiTextRequest.java`: optional provider thinking mode while preserving existing constructors.
- Modify `backend/src/main/java/com/antshorttv/ai/AbstractCompatibleProviderAdapter.java`: emit DeepSeek `thinking.type` only for DeepSeek model codes.
- Modify `backend/src/main/java/com/antshorttv/workflowagent/agent/EpisodeSplittingAgentBootstrap.java`: boundary-only operational prompt, tool allowlist, measured output budget, and step budget.
- Modify `backend/skills/short-drama-episode-splitting-framework/SKILL.md`: silent full-path and explicit fallback instructions.
- Create `backend/src/main/resources/db/migration/V80__episode_split_chunk_fallback.sql`: snapshot and chunk persistence.
- Create `backend/src/main/java/com/antshorttv/script/ScriptSplitSnapshotStore.java`: persisted snapshot/chunk state and retry queries.
- Create `backend/src/main/java/com/antshorttv/script/ScriptSplitChunkPlanner.java`: structure-aware Unicode-safe chunk ranges and trusted anchors.
- Create `backend/src/main/java/com/antshorttv/script/ScriptSplitChunkAnalyzer.java`: audited bounded-concurrency internal AI calls and candidate normalization.
- Create `backend/src/main/java/com/antshorttv/workflowagent/run/EpisodeSplittingRunPolicy.java`: preflight, fallback classification, clean fallback context, and mode state.
- Modify `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java`: delegate only split-specific preflight/fallback decisions to the policy.
- Modify `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunContract.java`: accept exactly the normal or fallback split sequence.
- Modify `backend/src/main/java/com/antshorttv/workflowagent/tool/WorkflowToolRunState.java`: record split mode and reset the required sequence without losing trusted scope.
- Modify `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolConfiguration.java`: register `read_script_structure` and `analyze_script_chunks` schemas.
- Modify `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java`: resolve the trusted script and delegate fallback tools.
- Modify `backend/src/main/java/com/antshorttv/script/ScriptWorkflowResponses.java`: expose split fallback progress.
- Modify `frontend/src/pages/projects/production-workbench/service.ts`: type split progress.
- Modify `frontend/src/pages/projects/production-workbench/script.tsx`: render fallback reason and chunk counts under the existing stage.
- Modify `frontend/src/pages/projects/production-workbench/script.test.tsx`: verify restored fallback progress.
- Modify `docs/short-drama-analysis-agents.md` and `env.example`: document controls, diagnostics, rollout, and rollback.

### Task 1: DeepSeek non-thinking request control

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/ai/AiTextRequest.java`
- Modify: `backend/src/main/java/com/antshorttv/ai/AbstractCompatibleProviderAdapter.java`
- Test: `backend/src/test/java/com/antshorttv/ai/OpenAiAdapterTest.java`

- [ ] **Step 1: Write failing adapter tests**

Add tests that capture `/chat/completions` request JSON and assert:

```java
AiTextRequest disabled = new AiTextRequest(
    null, "split", 0.2, 8192, null, false, null, 10, 0,
    List.of(AiChatMessage.user("split")), List.of(), "disabled"
);
adapter.text(provider(), config(baseUrl, "sk-real-123"),
    model("deepseek-v4-flash", "TEXT"), disabled, "split-1");
assertThat(new ObjectMapper().readTree(requestBody.get()).path("thinking").path("type").asText())
    .isEqualTo("disabled");

adapter.text(provider(), config(baseUrl, "sk-real-123"),
    model("gpt-test", "TEXT"), disabled, "split-2");
assertThat(new ObjectMapper().readTree(requestBody.get()).has("thinking")).isFalse();
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `cd backend; mvn -Dtest=OpenAiAdapterTest test`

Expected: compilation fails because `AiTextRequest` has no thinking-mode field, or the DeepSeek request lacks `thinking`.

- [ ] **Step 3: Add the request field and compatible constructor**

Append `String thinkingMode` to the record, validate only `enabled`/`disabled`, and retain the old full constructor:

```java
public AiTextRequest {
    messages = messages == null ? List.of() : List.copyOf(messages);
    tools = tools == null ? List.of() : List.copyOf(tools);
    if (thinkingMode != null && !Set.of("enabled", "disabled").contains(thinkingMode)) {
        throw new IllegalArgumentException("thinkingMode 必须为 enabled 或 disabled。");
    }
}

public AiTextRequest(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens,
    Double topP, Boolean jsonMode, Object responseSchema, Integer timeoutSeconds, Integer retryCount,
    List<AiChatMessage> messages, List<AiToolDefinition> tools) {
    this(systemPrompt, userPrompt, temperature, maxTokens, topP, jsonMode, responseSchema,
        timeoutSeconds, retryCount, messages, tools, null);
}
```

- [ ] **Step 4: Emit the vendor option only for DeepSeek**

In `chatPayload`, add:

```java
String modelCode = model.getModelCode() == null ? "" : model.getModelCode().toLowerCase(Locale.ROOT);
if (request.thinkingMode() != null && modelCode.startsWith("deepseek-")) {
    payload.put("thinking", Map.of("type", request.thinkingMode()));
}
```

- [ ] **Step 5: Run tests and commit**

Run: `cd backend; mvn -Dtest=OpenAiAdapterTest test`

Expected: PASS, including the existing native-tool and retry tests.

Commit:

```bash
git add backend/src/main/java/com/antshorttv/ai/AiTextRequest.java backend/src/main/java/com/antshorttv/ai/AbstractCompatibleProviderAdapter.java backend/src/test/java/com/antshorttv/ai/OpenAiAdapterTest.java
git commit -m "feat: support deepseek non-thinking requests"
```

Mark OpenSpec task `10.1` complete.

### Task 2: Boundary-only prompt and measured full-path budget

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/agent/EpisodeSplittingAgentBootstrap.java`
- Modify: `backend/skills/short-drama-episode-splitting-framework/SKILL.md`
- Test: `backend/src/test/java/com/antshorttv/workflowagent/agent/RemainingAnalysisAgentBootstrapTest.java`
- Test: `backend/src/test/java/com/antshorttv/workflowagent/skill/EpisodeSplittingSkillContractTest.java`
- Test: `backend/src/test/java/com/antshorttv/schema/EpisodeSplittingTokenBudgetMigrationTest.java`

- [ ] **Step 1: Add failing prompt and configuration assertions**

Assert the bootstrap prompt and Skill contain the operational constraints and four-tool allowlist:

```java
assertThat(command.systemPrompt())
    .contains("不要输出分析过程", "只提交标题和原文边界", "立即调用保存工具");
assertThat(command.toolCodes()).containsExactly(
    "read_current_script", "read_script_structure", "analyze_script_chunks",
    "save_episode_splitting");
assertThat(command.maxTokens()).isEqualTo(16384);
assertThat(command.maxSteps()).isEqualTo(8);
```

Assert the Skill says that chunk/structure anchors are analysis aids and cannot become formal boundaries without AI selection.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `cd backend; mvn -Dtest=RemainingAnalysisAgentBootstrapTest,EpisodeSplittingSkillContractTest,EpisodeSplittingTokenBudgetMigrationTest test`

Expected: failures on prompt, allowlist, `32768`, and old step count.

- [ ] **Step 3: Apply the minimal prompt/configuration change**

Use this bootstrap prompt and settings:

```java
"先按当前运行模式调用指定读取工具。读取后静默判断，不要输出分析过程、逐集论证或复述原文；只提交标题和逐字原文边界，并立即调用保存工具。分块模式只使用工具返回的候选与可信锚点。保存成功前不得声称完成。",
modelId, new BigDecimal("0.200"), 16384, 8, "ENABLED",
List.of("short-drama-analysis-foundation", "short-drama-episode-splitting-framework"),
List.of("read_current_script", "read_script_structure", "analyze_script_chunks",
    "save_episode_splitting")
```

Keep the already-applied V79 migration immutable. The idempotent bootstrap converges the enabled built-in Agent to 16K at startup; Task 3's V80 migration performs the same conditional `32768 -> 16384` correction for environments that run migrations before bootstraps, without overwriting administrator-customized values.

- [ ] **Step 4: Run focused tests and commit**

Run: `cd backend; mvn -Dtest=RemainingAnalysisAgentBootstrapTest,EpisodeSplittingSkillContractTest,EpisodeSplittingTokenBudgetMigrationTest test`

Expected: PASS.

Commit:

```bash
git add backend/src/main/java/com/antshorttv/workflowagent/agent/EpisodeSplittingAgentBootstrap.java backend/skills/short-drama-episode-splitting-framework/SKILL.md backend/src/test/java/com/antshorttv/workflowagent/agent/RemainingAnalysisAgentBootstrapTest.java backend/src/test/java/com/antshorttv/workflowagent/skill/EpisodeSplittingSkillContractTest.java backend/src/test/java/com/antshorttv/schema/EpisodeSplittingTokenBudgetMigrationTest.java
git commit -m "feat: constrain episode split output"
```

Mark OpenSpec task `10.2` complete.

### Task 3: Persist fallback snapshots and chunk units

**Files:**
- Create: `backend/src/main/resources/db/migration/V80__episode_split_chunk_fallback.sql`
- Create: `backend/src/main/java/com/antshorttv/script/ScriptSplitSnapshotStore.java`
- Create: `backend/src/test/java/com/antshorttv/schema/EpisodeSplitFallbackMigrationTest.java`
- Create: `backend/src/test/java/com/antshorttv/script/ScriptSplitSnapshotStoreTest.java`
- Modify: `backend/src/test/java/com/antshorttv/schema/SchemaMigrationTest.java`

- [ ] **Step 1: Write failing migration assertions**

Require tables and indexes with the following essential columns:

```java
assertThat(columns("script_split_snapshot")).contains(
    "tenant_id", "project_id", "script_id", "parent_run_id", "content_hash",
    "mode", "fallback_reason", "status", "planner_version", "total_chunks",
    "completed_chunks", "failed_chunks", "created_at", "finished_at", "updated_at");
assertThat(columns("script_split_chunk")).contains(
    "snapshot_id", "chunk_no", "core_start", "core_end", "context_start",
    "context_end", "content_hash", "status", "ai_call_log_id", "candidate_json",
    "error_code", "error_message", "created_at", "updated_at");
```

- [ ] **Step 2: Run migration tests and verify RED**

Run: `cd backend; mvn -Dtest=EpisodeSplitFallbackMigrationTest,SchemaMigrationTest test`

Expected: tables are absent.

- [ ] **Step 3: Add V80 with strict ownership and retry indexes**

Create both tables with foreign keys to `script`, `workflow_agent_run`, `ai_call_log`, and snapshot; use `json` for `candidate_json`; add unique `(snapshot_id, chunk_no)`, lookup `(tenant_id, script_id, content_hash, status)`, and retry `(snapshot_id, status, chunk_no)` indexes. Do not store script body text. In the same V80 migration, conditionally update the built-in split Agent from 32768 to 16384 tokens while leaving every other configured value untouched.

- [ ] **Step 4: Write failing store tests**

Cover create-or-resume, monotonic counts, successful-unit reuse, failed-only selection, and stale marking:

```java
long snapshotId = store.createOrResume(scope, "hash-a", "LENGTH", 12, "planner-v1");
store.markChunkSucceeded(snapshotId, 1, 9001L, candidatesJson);
assertThat(store.retryableChunks(snapshotId)).extracting(SplitChunk::chunkNo)
    .doesNotContain(1);
store.markStaleForDifferentHash(scope, "hash-b");
assertThat(store.require(snapshotId).status()).isEqualTo("STALE");
```

- [ ] **Step 5: Implement the JDBC store and run tests**

The store must expose typed records and transactional methods:

```java
public record SplitSnapshot(long id, long parentRunId, String contentHash, String mode,
    String fallbackReason, String status, int total, int completed, int failed) {}
public record SplitChunk(long id, long snapshotId, int chunkNo, int coreStart, int coreEnd,
    int contextStart, int contextEnd, String contentHash, String status,
    Long aiCallLogId, JsonNode candidates) {}
```

Run: `cd backend; mvn -Dtest=EpisodeSplitFallbackMigrationTest,ScriptSplitSnapshotStoreTest,SchemaMigrationTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V80__episode_split_chunk_fallback.sql backend/src/main/java/com/antshorttv/script/ScriptSplitSnapshotStore.java backend/src/test/java/com/antshorttv/schema/EpisodeSplitFallbackMigrationTest.java backend/src/test/java/com/antshorttv/script/ScriptSplitSnapshotStoreTest.java backend/src/test/java/com/antshorttv/schema/SchemaMigrationTest.java
git commit -m "feat: persist episode split fallback state"
```

Mark OpenSpec tasks `10.3` and `10.4` complete.

### Task 4: Structure-aware chunk planning and trusted structure tool

**Files:**
- Create: `backend/src/main/java/com/antshorttv/script/ScriptSplitChunkPlanner.java`
- Create: `backend/src/test/java/com/antshorttv/script/ScriptSplitChunkPlannerTest.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolConfiguration.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolCatalogTest.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataServiceTest.java`

- [ ] **Step 1: Write planner tests**

Use explicit headings, scene headings without episode labels, long paragraphs, and an emoji-surrogate boundary. Assert:

```java
List<ChunkPlan> chunks = planner.plan(source, new ChunkSettings(15_000, 20_000, 24_000, 1_500));
assertThat(chunks).allSatisfy(chunk -> {
    assertThat(chunk.contextEnd() - chunk.contextStart()).isLessThanOrEqualTo(24_000);
    assertThat(Character.isHighSurrogate(source.charAt(Math.max(0, chunk.contextEnd() - 1))))
        .isFalse();
});
assertThat(chunks.get(1).contextStart()).isLessThan(chunks.get(0).contextEnd());
assertThat(chunks).extracting(ChunkPlan::boundarySignal)
    .containsAnyOf("EPISODE_HEADING", "SCENE_HEADING", "PARAGRAPH", "LINE", "HARD_LIMIT");
```

- [ ] **Step 2: Run and verify RED**

Run: `cd backend; mvn -Dtest=ScriptSplitChunkPlannerTest test`

Expected: planner types do not exist.

- [ ] **Step 3: Implement the deterministic planner**

Define:

```java
public record ChunkSettings(int targetMin, int targetMax, int hardMax, int overlap) {}
public record TrustedAnchor(int offset, String marker, String signal) {}
public record ChunkPlan(int chunkNo, int coreStart, int coreEnd, int contextStart,
    int contextEnd, String boundarySignal, List<TrustedAnchor> anchors) {}
public List<ChunkPlan> plan(String source, ChunkSettings settings)
```

Search backward from `targetMax` to `targetMin` using ordered regex/index candidates; hard-cut only when none exist. Normalize every cut with `Character.offsetByCodePoints` so no surrogate pair is split. Limit each anchor marker to 500 characters and make its absolute offset authoritative.

- [ ] **Step 4: Add failing tool-schema and trusted-scope tests**

Require empty input and output fields `contentHash`, `snapshotKey`, `totalChunks`, `chunks`, and `anchors`. Assert model-supplied IDs are rejected and chunk output never contains the complete script body.

- [ ] **Step 5: Register and implement `read_script_structure`**

The tool calls `ScriptSplitChunkPlanner`, creates/resumes a snapshot in `ScriptSplitSnapshotStore`, records `splitSnapshotId` and `splitContentHash` in `WorkflowToolRunState`, and returns opaque `snapshotKey` rather than a database ID.

- [ ] **Step 6: Run tests and commit**

Run: `cd backend; mvn -Dtest=ScriptSplitChunkPlannerTest,ScreenplayToolCatalogTest,ScreenplayToolDataServiceTest test`

Expected: PASS.

Commit:

```bash
git add backend/src/main/java/com/antshorttv/script/ScriptSplitChunkPlanner.java backend/src/test/java/com/antshorttv/script/ScriptSplitChunkPlannerTest.java backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolConfiguration.java backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolCatalogTest.java backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataServiceTest.java
git commit -m "feat: plan trusted episode split chunks"
```

Mark OpenSpec tasks `10.5` and `10.6` complete.

### Task 5: Audited chunk analysis and candidate aggregation

**Files:**
- Create: `backend/src/main/java/com/antshorttv/script/ScriptSplitChunkAnalyzer.java`
- Create: `backend/src/test/java/com/antshorttv/script/ScriptSplitChunkAnalyzerTest.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolConfiguration.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/WorkflowAgentProperties.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Write failing analyzer tests**

Stub internal calls for three overlapping chunks and assert absolute candidate verification, deduplication, concurrency cap, and failed-only retry:

```java
ChunkAnalysisResult result = analyzer.analyze(context, snapshotId);
assertThat(result.total()).isEqualTo(3);
assertThat(result.candidates()).extracting(BoundaryCandidate::absoluteOffset)
    .containsExactly(18_240, 37_510);
assertThat(result.candidates().get(0).sourceChunks()).containsExactlyInAnyOrder(1, 2);
assertThat(maxObservedConcurrency.get()).isLessThanOrEqualTo(3);
verify(invocation, times(3)).invokeText(any());
```

For one failed chunk, assert no successful result is returned and the next call invokes only that chunk.

- [ ] **Step 2: Run and verify RED**

Run: `cd backend; mvn -Dtest=ScriptSplitChunkAnalyzerTest test`

Expected: analyzer types do not exist.

- [ ] **Step 3: Implement the analyzer contract**

Define immutable outputs:

```java
public record BoundaryCandidate(String marker, int absoluteOffset, String type,
    String rationale, double confidence, List<Integer> sourceChunks) {}
public record ChunkAnalysisResult(int total, int completed, List<BoundaryCandidate> candidates,
    List<ScriptSplitChunkPlanner.TrustedAnchor> anchors, List<Long> aiCallLogIds) {}
```

Each internal request uses the same execution/attempt identity, phase `EPISODE_SPLIT_CHUNK_<n>`, idempotency `agent-run-<run>-split-chunk-<snapshot>-<n>`, non-thinking mode, a 4K output cap, and a JSON schema. Convert local offsets to absolute offsets, verify `source.startsWith(marker, absoluteOffset)`, reject the entire chunk on any invalid marker, then merge equal absolute offsets.

- [ ] **Step 4: Add configuration**

Add validated properties and defaults:

```yaml
ai:
  workflow-agent:
    split-chunk-target-min: ${AI_WORKFLOW_SPLIT_CHUNK_TARGET_MIN:15000}
    split-chunk-target-max: ${AI_WORKFLOW_SPLIT_CHUNK_TARGET_MAX:20000}
    split-chunk-hard-max: ${AI_WORKFLOW_SPLIT_CHUNK_HARD_MAX:24000}
    split-chunk-overlap: ${AI_WORKFLOW_SPLIT_CHUNK_OVERLAP:1500}
    split-chunk-concurrency: ${AI_WORKFLOW_SPLIT_CHUNK_CONCURRENCY:3}
```

- [ ] **Step 5: Register `analyze_script_chunks`**

Use empty input. Require `splitSnapshotId` from Run state, call the analyzer, and return only bounded candidates/anchors/counts/call references. Reject candidate output beyond `max-log-payload-bytes` before writing tool logs.

- [ ] **Step 6: Run tests and commit**

Run: `cd backend; mvn -Dtest=ScriptSplitChunkAnalyzerTest,ScreenplayToolCatalogTest,ScreenplayToolDataServiceTest test`

Expected: PASS.

Commit:

```bash
git add backend/src/main/java/com/antshorttv/script/ScriptSplitChunkAnalyzer.java backend/src/test/java/com/antshorttv/script/ScriptSplitChunkAnalyzerTest.java backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolConfiguration.java backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java backend/src/main/java/com/antshorttv/workflowagent/WorkflowAgentProperties.java backend/src/main/resources/application.yml
git commit -m "feat: analyze episode split chunks"
```

Mark OpenSpec tasks `10.7` and `10.8` complete.

### Task 6: Full-path preflight and clean automatic fallback

**Files:**
- Create: `backend/src/main/java/com/antshorttv/workflowagent/run/EpisodeSplittingRunPolicy.java`
- Create: `backend/src/test/java/com/antshorttv/workflowagent/run/EpisodeSplittingRunPolicyTest.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunContract.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/WorkflowToolRunState.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java`
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/run/RemainingAnalysisAgentRunContractTest.java`

- [ ] **Step 1: Write policy classification tests**

Assert these mappings:

```java
assertThat(policy.classify(response("length", true, "text", List.of()), stateAfterRead()))
    .isEqualTo(FallbackReason.OUTPUT_TRUNCATED);
assertThat(policy.classify(response("stop", false, "", List.of()), stateAfterRead()))
    .isEqualTo(FallbackReason.EMPTY_RESPONSE);
assertThat(policy.classify(response("stop", false, "done", List.of()), stateAfterRead()))
    .isEqualTo(FallbackReason.SAVE_NOT_CALLED);
assertThat(policy.classifyToolFailure(ErrorCode.SCRIPT_CONTENT_CHANGED)).isEmpty();
assertThat(policy.classifyToolFailure(ErrorCode.VALIDATION_ERROR)).isEmpty();
```

Test the conservative estimate `ceil(utf8Bytes / 3.0) + promptReserve + toolReserve` against a configurable safe context token limit; exact provider tokenization is not required for safety.

- [ ] **Step 2: Run policy tests and verify RED**

Run: `cd backend; mvn -Dtest=EpisodeSplittingRunPolicyTest test`

Expected: policy does not exist.

- [ ] **Step 3: Implement policy and dynamic contract state**

Provide:

```java
public enum SplitMode { FULL, CHUNK_FALLBACK }
public enum FallbackReason { CONTEXT_PREFLIGHT, CONTEXT_ERROR, OUTPUT_TRUNCATED,
    EMPTY_RESPONSE, SAVE_NOT_CALLED }
public record SplitDecision(SplitMode mode, FallbackReason reason) {}
```

`WorkflowToolRunState` records `splitMode`, `fallbackReason`, and successful tools per active mode. `WorkflowAgentRunContract` accepts either:

```java
List.of("read_current_script", "save_episode_splitting")
List.of("read_script_structure", "analyze_script_chunks", "save_episode_splitting")
```

It must reject mixed sequences, repeated fallback transitions, and more than one terminal save.

- [ ] **Step 4: Write failing runner integration tests**

Cover:

1. Normal read then save completes with two model calls.
2. Preflight selects fallback before the first model call.
3. A truncated second round records the call, clears messages, switches once, and calls structure/analyze/save.
4. Clean fallback messages contain only system prompt, a fallback instruction, and fallback tool results—not the full script tool result or truncated content.
5. A save-tool validation error returns to the model or fails according to its existing policy and never switches modes.
6. Other Agent codes keep existing behavior.

- [ ] **Step 5: Integrate the split policy into the runner**

Inject `EpisodeSplittingRunPolicy`. Before constructing initial messages, ask it for a preflight mode using trusted script metadata. After any no-tool response and before `contract.requireComplete`, ask it whether the split Run may switch. On switch:

```java
runState.beginSplitFallback(reason.name());
messages.clear();
messages.add(AiChatMessage.system(prompt));
messages.add(AiChatMessage.user(
    "全文边界分析未完成，原因：" + reason.name()
        + "。立即调用 read_script_structure，随后调用 analyze_script_chunks，"
        + "最后仅用可信候选调用 save_episode_splitting。"
));
```

Every fallback model request uses phase `AGENT_FALLBACK_STEP_<n>` and a distinct idempotency key. Pass `thinkingMode="disabled"` for every split Agent model round. Do not change request construction for other Agent codes.

- [ ] **Step 6: Run tests and commit**

Run: `cd backend; mvn -Dtest=EpisodeSplittingRunPolicyTest,WorkflowAgentRunnerTest,RemainingAnalysisAgentRunContractTest test`

Expected: PASS.

Commit:

```bash
git add backend/src/main/java/com/antshorttv/workflowagent/run/EpisodeSplittingRunPolicy.java backend/src/test/java/com/antshorttv/workflowagent/run/EpisodeSplittingRunPolicyTest.java backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunContract.java backend/src/main/java/com/antshorttv/workflowagent/tool/WorkflowToolRunState.java backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java backend/src/test/java/com/antshorttv/workflowagent/run/RemainingAnalysisAgentRunContractTest.java
git commit -m "feat: add episode split fallback policy"
```

Mark OpenSpec tasks `10.9` and `10.10` complete.

### Task 7: Restore fallback progress in the existing splitting stage

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/script/ScriptWorkflowResponses.java`
- Modify: `backend/src/main/java/com/antshorttv/script/ScriptWorkflowService.java`
- Modify: `backend/src/test/java/com/antshorttv/script/ScriptAnalysisTaskServiceTest.java`
- Modify: `backend/src/test/java/com/antshorttv/script/ScriptAnalysisExecutionServiceTest.java`
- Modify: `frontend/src/pages/projects/production-workbench/service.ts`
- Modify: `frontend/src/pages/projects/production-workbench/script.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/script.test.tsx`

- [ ] **Step 1: Add failing backend response tests**

Add a nullable response object:

```java
record EpisodeSplitProgressResponse(
    String mode,
    String fallbackReason,
    Integer totalChunks,
    Integer completedChunks,
    Integer failedChunks,
    Boolean stale
) {}
```

Assert a running snapshot returns `CHUNK_FALLBACK`, `OUTPUT_TRUNCATED`, `12/7/1`, while a normal Run returns `FULL` with zero chunk counts.

- [ ] **Step 2: Run backend tests and verify RED**

Run: `cd backend; mvn -Dtest=ScriptAnalysisTaskServiceTest,ScriptAnalysisExecutionServiceTest test`

Expected: stage response has no split progress.

- [ ] **Step 3: Implement backend projection**

Query the newest non-stale split snapshot by `parent_run_id` or stage/run scope and attach it only to `EPISODE_SPLITTING`. Continue using stage `completedUnits/totalUnits` for episode-stage semantics; chunk counts live only inside `splitProgress`.

- [ ] **Step 4: Add failing frontend rendering test**

Use this API fragment:

```ts
splitProgress: {
  mode: 'CHUNK_FALLBACK',
  fallbackReason: 'OUTPUT_TRUNCATED',
  totalChunks: 12,
  completedChunks: 7,
  failedChunks: 1,
  stale: false,
}
```

Assert the page shows `分块分析 7/12` and `全文输出达到上限，已自动切换`, still renders exactly four stage labels, and does not append `集` to chunk counts.

- [ ] **Step 5: Implement types and compact UI**

Add:

```ts
export type EpisodeSplitProgress = {
  mode: 'FULL' | 'CHUNK_FALLBACK';
  fallbackReason?: string | null;
  totalChunks: number;
  completedChunks: number;
  failedChunks: number;
  stale: boolean;
};
```

Render progress only below the splitting stage. Map known reasons to Chinese text; unknown reasons display `已自动切换到分块分析` without exposing provider payloads.

- [ ] **Step 6: Run tests and commit**

Run: `cd backend; mvn -Dtest=ScriptAnalysisTaskServiceTest,ScriptAnalysisExecutionServiceTest test`

Run: `cd frontend; npm test -- --run src/pages/projects/production-workbench/script.test.tsx`

Expected: both PASS.

Commit:

```bash
git add backend/src/main/java/com/antshorttv/script/ScriptWorkflowResponses.java backend/src/main/java/com/antshorttv/script/ScriptWorkflowService.java backend/src/test/java/com/antshorttv/script/ScriptAnalysisTaskServiceTest.java backend/src/test/java/com/antshorttv/script/ScriptAnalysisExecutionServiceTest.java frontend/src/pages/projects/production-workbench/service.ts frontend/src/pages/projects/production-workbench/script.tsx frontend/src/pages/projects/production-workbench/script.test.tsx
git commit -m "feat: show episode split fallback progress"
```

Mark OpenSpec task `10.11` complete.

### Task 8: End-to-end failure boundaries and formal save invariants

**Files:**
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentEndToEndSmokeTest.java`
- Modify: `backend/src/test/java/com/antshorttv/script/LiveLatestProjectAgentSmokeTest.java`
- Modify: `backend/src/test/java/com/antshorttv/script/ScriptEpisodeFormalReconciliationTest.java`

- [ ] **Step 1: Add deterministic end-to-end tests**

Use a fake provider sequence for a multi-episode long script:

```text
full round 1 -> read_current_script
full round 2 -> finish_reason=length, no tool call
fallback round 1 -> read_script_structure
fallback round 2 -> analyze_script_chunks
fallback round 3 -> save_episode_splitting with verified markers
```

Assert one parent Run, all model-call logs retained, one successful `save_episode_splitting`, exact concatenated episode content equal to the original script, and stable IDs on an idempotent rerun.

- [ ] **Step 2: Add failure-path tests**

Cover context preflight, invalid chunk marker, one failed chunk, stale script before save, save validation error without fallback, transaction rollback, retry of only failed chunks, and cancellation before unscheduled chunks. Assert no formal episode mutation until the terminal save succeeds.

- [ ] **Step 3: Run focused end-to-end tests**

Run: `cd backend; mvn -Dtest=WorkflowAgentEndToEndSmokeTest,ScriptEpisodeFormalReconciliationTest test`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentEndToEndSmokeTest.java backend/src/test/java/com/antshorttv/script/LiveLatestProjectAgentSmokeTest.java backend/src/test/java/com/antshorttv/script/ScriptEpisodeFormalReconciliationTest.java
git commit -m "test: cover episode split fallback end to end"
```

Mark OpenSpec tasks `9.3`, `9.4`, and the deterministic portion of `9.5` complete only when their complete existing acceptance criteria pass.

### Task 9: Configuration documentation and full verification

**Files:**
- Modify: `env.example`
- Modify: `docs/short-drama-analysis-agents.md`
- Modify: `openspec/changes/add-remaining-short-drama-analysis-agents/tasks.md`

- [ ] **Step 1: Document exact settings and operating signals**

Add these variables with their defaults:

```dotenv
AI_WORKFLOW_SPLIT_SAFE_CONTEXT_TOKENS=800000
AI_WORKFLOW_SPLIT_PROMPT_RESERVE_TOKENS=12000
AI_WORKFLOW_SPLIT_TOOL_RESERVE_TOKENS=24000
AI_WORKFLOW_SPLIT_CHUNK_TARGET_MIN=15000
AI_WORKFLOW_SPLIT_CHUNK_TARGET_MAX=20000
AI_WORKFLOW_SPLIT_CHUNK_HARD_MAX=24000
AI_WORKFLOW_SPLIT_CHUNK_OVERLAP=1500
AI_WORKFLOW_SPLIT_CHUNK_CONCURRENCY=3
```

Document normal/fallback sequences, DeepSeek non-thinking behavior, retry reuse, stale invalidation, error codes, expected model-call counts, billing aggregation, rollout order, and rollback by disabling `AI_WORKFLOW_EPISODE_SPLITTING_ENABLED`.

- [ ] **Step 2: Run focused backend verification**

Run:

```powershell
cd backend
mvn -Dtest=OpenAiAdapterTest,RemainingAnalysisAgentBootstrapTest,EpisodeSplittingSkillContractTest,EpisodeSplitFallbackMigrationTest,ScriptSplitSnapshotStoreTest,ScriptSplitChunkPlannerTest,ScriptSplitChunkAnalyzerTest,EpisodeSplittingRunPolicyTest,WorkflowAgentRunnerTest,RemainingAnalysisAgentRunContractTest,ScreenplayToolCatalogTest,ScreenplayToolDataServiceTest,WorkflowAgentEndToEndSmokeTest,ScriptEpisodeFormalReconciliationTest test
```

Expected: zero failures and zero errors.

- [ ] **Step 3: Run full repository verification**

Run:

```powershell
cd backend
mvn test
cd ../frontend
npm run tsc
npm test
npm run biome:lint
npx antd lint ./src
```

Expected: all commands exit `0`.

- [ ] **Step 4: Update OpenSpec and commit documentation**

Mark `10.12` complete only after Step 3. Keep `9.5`, `9.7`, and `10.13` open until the real-provider smoke test succeeds.

```bash
git add env.example docs/short-drama-analysis-agents.md openspec/changes/add-remaining-short-drama-analysis-agents/tasks.md openspec/changes/add-remaining-short-drama-analysis-agents/proposal.md openspec/changes/add-remaining-short-drama-analysis-agents/design.md openspec/changes/add-remaining-short-drama-analysis-agents/specs/short-drama-episode-splitting-agent/spec.md openspec/changes/add-remaining-short-drama-analysis-agents/specs/script-analysis-progress/spec.md
git commit -m "docs: document episode split fallback"
```

### Task 10: Latest-project real-provider smoke test

**Files:**
- Modify only if the test needs non-secret controls: `backend/src/test/java/com/antshorttv/script/LiveLatestProjectAgentSmokeTest.java`
- Update after evidence: `openspec/changes/add-remaining-short-drama-analysis-agents/tasks.md`

- [ ] **Step 1: Restart the backend with the four Agent flags and fallback defaults**

Use the existing local encrypted provider credentials from `backend/env`; never print them. Start the freshly built JAR and wait for `/actuator/health` or the existing authenticated health check to report ready.

- [ ] **Step 2: Run project 26 through the normal path**

Run the live test with its existing explicit opt-in environment flag. Verify the DeepSeek request contains `thinking.type=disabled`, the model calls `save_episode_splitting`, concatenated formal episode bodies equal the exact 22.8万-character source, and the call log does not end with `finish_reason=length`.

- [ ] **Step 3: Force fallback safely**

Set `AI_WORKFLOW_SPLIT_SAFE_CONTEXT_TOKENS` below the project 26 estimate, rerun splitting, and verify the same visible stage records `CONTEXT_PREFLIGHT`, persists all chunk units, aggregates candidates in a clean context, saves exactly once, and retains stable episode IDs where reconciliation evidence is strong.

- [ ] **Step 4: Verify restore, billing, and downstream startup**

Reload the workspace while fallback is active and after completion. Confirm chunk counts restore, total call tokens/cost include internal calls, and summary plus asset-recognition can start against the resulting first formal episode.

- [ ] **Step 5: Mark evidence-backed tasks complete**

Mark `9.5`, `9.7`, and `10.13` complete only after recording run IDs, model call IDs, counts, hashes, episode count, stable-ID comparison, and downstream Run IDs in the local smoke-test output. Do not store credentials or full script content.

- [ ] **Step 6: Commit the final task state**

```bash
git add openspec/changes/add-remaining-short-drama-analysis-agents/tasks.md backend/src/test/java/com/antshorttv/script/LiveLatestProjectAgentSmokeTest.java
git commit -m "test: verify episode split fallback live"
```

Finally run:

```powershell
openspec validate add-remaining-short-drama-analysis-agents --type change --strict --no-interactive
openspec status --change add-remaining-short-drama-analysis-agents
```

Expected: validation passes; all implementation and live-smoke tasks are complete before archiving.
