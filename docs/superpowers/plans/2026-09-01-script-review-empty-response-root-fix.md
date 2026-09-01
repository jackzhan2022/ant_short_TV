# Script Review Empty-Response Root Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent DeepSeek hidden reasoning from exhausting script-review output tokens and recover a bounded truncated response without losing the workflow contract.

**Architecture:** Keep the change inside `WorkflowAgentRunner`: select disabled thinking for tool-driven script review calls and return one corrective message to the model when a response is explicitly truncated. Reuse the current contract, audit, timeout, and shared step-budget mechanisms.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, AssertJ, Mockito, Maven

---

### Task 1: Reproduce the production truncation path

**Files:**
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java`

- [ ] **Step 1: Write the failing test**

Add a script-review runner test whose invocation sequence is context read, content read, a truncated empty response, history read, and terminal save. Capture provider requests and assert `thinkingMode()` is `disabled` for every request. Assert the save executes once and the run completes.

```java
when(invocation.invokeText(any()))
    .thenReturn(result(null, List.of(new AiToolCall("context", "read_review_context", "{}")), 621L))
    .thenReturn(result(null, List.of(new AiToolCall("content", "read_review_content", "{}")), 622L))
    .thenReturn(truncated(623L))
    .thenReturn(result(null, List.of(new AiToolCall("history", "read_review_issue_history", "{}")), 624L))
    .thenReturn(result(null, List.of(new AiToolCall("save", "save_review_unit_result", "{}")), 625L));
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```powershell
mvn -q '-Dtest=WorkflowAgentRunnerTest#scriptReviewDisablesThinkingAndRecoversTruncatedEmptyResponse' test
```

Expected: FAIL because script-review requests have a null thinking mode and the truncated response terminates with `REQUIRED_TOOL_NOT_CALLED`.

- [ ] **Step 3: Commit the failing regression test**

```powershell
git add backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java
git commit -m "test: reproduce truncated script review response"
```

### Task 2: Disable thinking and recover explicit truncation

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java`

- [ ] **Step 1: Disable thinking for script-review requests**

Replace the current splitting-only thinking selection with a named helper:

```java
.textRequest(new AiTextRequest(
    null, null, agent.temperature().doubleValue(), agent.maxTokens(), null, false,
    null, remainingSeconds(deadline), 0,
    messages, activeProviderTools(allowedTools, splitting, runState),
    disableThinking(agent.code(), splitting) ? "disabled" : null
))
```

```java
private boolean disableThinking(String agentCode, boolean splitting) {
    return splitting || "script-review".equals(agentCode);
}
```

- [ ] **Step 2: Continue a truncated incomplete script-review call**

Before `contract.requireComplete(runState)`, detect an explicitly truncated response with no tool calls and return a bounded correction to the model:

```java
if ("script-review".equals(agent.code())
    && response != null
    && (response.truncated() || "length".equalsIgnoreCase(response.finishReason()))) {
    messages.add(AiChatMessage.user(
        "模型输出已截断，审核契约尚未完成。不得输出普通文本；"
            + "立即调用尚未完成的可信读取工具，完成后调用保存工具。"));
    continue;
}
```

- [ ] **Step 3: Run the focused test to verify GREEN**

Run:

```powershell
mvn -q '-Dtest=WorkflowAgentRunnerTest#scriptReviewDisablesThinkingAndRecoversTruncatedEmptyResponse' test
```

Expected: PASS.

- [ ] **Step 4: Run workflow regression tests**

Run:

```powershell
mvn -q '-Dtest=WorkflowAgentRunnerTest,ScriptReviewAgentRunContractTest' test
```

Expected: PASS with no test failures.

- [ ] **Step 5: Commit the implementation**

```powershell
git add backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java
git commit -m "fix: prevent truncated script review responses"
```

### Task 3: Release and production verification

**Files:**
- No source changes.

- [ ] **Step 1: Package the backend and verify the executable JAR**

```powershell
mvn -q -DskipTests package
jar tf backend/target/ant-short-tv-backend-0.1.0-SNAPSHOT.jar | Select-String 'BOOT-INF/classes'
```

Expected: package succeeds and `BOOT-INF/classes/` is present.

- [ ] **Step 2: Push and deploy through the versioned-release runbook**

Follow `docs/antv-deployment-runbook.md`: create matched database/Skill backups, upload artifacts, compare SHA-256 hashes, atomically switch `/opt/antv/current`, and restart `antv.service`.

- [ ] **Step 3: Verify production health**

Expected checks: `antv.service` is active with no restarts, `/api/currentUser` returns 401 without authentication, and `https://antv.aixmax.cn/` returns 200.

- [ ] **Step 4: Retry and verify review task 9**

Retry the failed task from the signed-in review workbench. Verify the new `ai_workflow_agent_run` uses successful tool steps after `read_review_content`, unit 1 reaches `COMPLETED` with `candidate_saved=1`, and the task advances beyond 20 percent.
