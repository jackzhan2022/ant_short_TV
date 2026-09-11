# Production Workspace Backend Read Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make focused production-workbench APIs execute bounded database reads, avoid repeated authorization work, and emit safe request-level SQL timing evidence for project 33 verification.

**Architecture:** Add a lightweight episode navigation projection used by script and storyboard reads, batch analysis-stage relations before response assembly, and pass one verified project context through each public read. Instrument MyBatis query execution and selected HTTP routes with a request-scoped collector using constant route labels and no credentials or business content.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Spring MVC, JUnit 5, Mockito, Maven, OpenSpec.

---

### Task 1: Extend the OpenSpec acceptance boundary

**Files:**
- Modify: `openspec/changes/split-production-workspace-apis/design.md`
- Modify: `openspec/changes/split-production-workspace-apis/specs/production-workspace-query-boundaries/spec.md`
- Modify: `openspec/changes/split-production-workspace-apis/specs/script-analysis-progress/spec.md`
- Modify: `openspec/changes/split-production-workspace-apis/tasks.md`

- [ ] **Step 1: Add the verified production findings**

Document that focused reads still spend most time waiting and storyboard responses contain all episode bodies.

- [ ] **Step 2: Add bounded-read requirements and tasks**

Require lightweight episode SQL projections, constant-query analysis assembly, one authorization resolution per public read, and sanitized request-level SQL timing evidence. Keep task 7.2 pending.

- [ ] **Step 3: Validate artifacts**

Run: `openspec status --change split-production-workspace-apis --json`

Expected: all artifacts remain `done`; task count includes the new unchecked work.

- [ ] **Step 4: Commit**

```bash
git add openspec/changes/split-production-workspace-apis
git commit -m "docs(workbench): specify bounded backend reads"
```

### Task 2: Introduce a lightweight episode navigation query

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/script/AssetDomainMappers.java`
- Modify: `backend/src/main/java/com/antshorttv/script/ScriptEpisodeService.java`
- Modify: `backend/src/main/java/com/antshorttv/script/ScriptWorkflowResponses.java`
- Test: `backend/src/test/java/com/antshorttv/script/ScriptEpisodeServiceTest.java`
- Create: `backend/src/test/java/com/antshorttv/script/ScriptWorkflowReadBoundaryTest.java`

- [ ] **Step 1: Write failing navigation projection tests**

Seed episodes with unique large bodies. Assert the navigation query returns ordered metadata without a `content` accessor or serialized body. Assert script and storyboard focused reads use navigation rather than `currentEpisodes` on the structured path.

- [ ] **Step 2: Verify RED**

Run: `mvn -f backend/pom.xml -Dtest=ScriptEpisodeServiceTest,ScriptWorkflowReadBoundaryTest test`

Expected: FAIL because the projection is missing and focused reads still load complete episodes.

- [ ] **Step 3: Implement the minimal projection and migrate focused reads**

Add `ScriptEpisodeNavigation` and an explicit mapper query whose SQL column list excludes `content`. Use it in `scriptPageWorkspace` and `storyboardWorkspace`; preserve the parser fallback only when structured episodes are absent. Keep `currentEpisodes` unchanged for agent/write compatibility.

- [ ] **Step 4: Verify GREEN**

Run: `mvn -f backend/pom.xml -Dtest=ScriptEpisodeServiceTest,ScriptWorkflowReadBoundaryTest,ScriptWorkflowControllerTest test`

Expected: PASS; both focused JSON responses exclude episode `content`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/antshorttv/script backend/src/test/java/com/antshorttv/script
git commit -m "perf(script): query lightweight episode navigation"
```

### Task 3: Batch analysis-state reads

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/script/ScriptAnalysisMappers.java`
- Modify: `backend/src/main/java/com/antshorttv/script/ScriptWorkflowService.java`
- Create: `backend/src/test/java/com/antshorttv/script/ScriptAnalysisReadBoundaryTest.java`
- Test: `backend/src/test/java/com/antshorttv/script/ScriptWorkflowControllerTest.java`

- [ ] **Step 1: Write a failing constant-query test**

Compare equivalent one-stage and multi-stage fixtures. Assert query count does not grow with stage count while stage order, latest result selection, Run IDs, fan-out and split defaults remain unchanged.

- [ ] **Step 2: Verify RED**

Run: `mvn -f backend/pom.xml -Dtest=ScriptAnalysisReadBoundaryTest test`

Expected: FAIL because response assembly queries inside the stage loop.

- [ ] **Step 3: Add batch reads and in-memory assembly**

Batch latest results and Agent Runs by stage IDs. Batch fan-out/split snapshots, units and statistics by their parent IDs. Retain existing single-stage mapper methods for execution paths.

- [ ] **Step 4: Verify GREEN and compatibility**

Run: `mvn -f backend/pom.xml -Dtest=ScriptAnalysisReadBoundaryTest,ScriptWorkflowControllerTest,EpisodeSplitWarningsTest test`

Expected: PASS with unchanged API semantics and bounded query count.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/antshorttv/script backend/src/test/java/com/antshorttv/script
git commit -m "perf(analysis): batch workspace status reads"
```

### Task 4: Reuse one verified access context per focused read

**Files:**
- Modify: `backend/src/main/java/com/antshorttv/script/ScriptWorkflowService.java`
- Create: `backend/src/test/java/com/antshorttv/script/ScriptWorkflowAccessBoundaryTest.java`
- Test: `backend/src/test/java/com/antshorttv/script/ScriptWorkflowControllerTest.java`

- [ ] **Step 1: Write failing verification-count tests**

Assert one active-membership resolution and one project-access resolution per focused read while retaining cross-tenant/project denial coverage.

- [ ] **Step 2: Verify RED**

Run: `mvn -f backend/pom.xml -Dtest=ScriptWorkflowAccessBoundaryTest test`

Expected: FAIL for methods that currently resolve membership more than once.

- [ ] **Step 3: Pass the verified context inward**

Resolve access once at each public boundary. Refactor only private read helpers reached from those methods so they consume the verified context instead of re-resolving it. Do not cache across requests.

- [ ] **Step 4: Verify GREEN and isolation**

Run: `mvn -f backend/pom.xml -Dtest=ScriptWorkflowAccessBoundaryTest,ScriptWorkflowControllerTest test`

Expected: PASS with unchanged denial behavior.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/antshorttv/script/ScriptWorkflowService.java backend/src/test/java/com/antshorttv/script
git commit -m "perf(auth): reuse workspace access context"
```

### Task 5: Add sanitized request-level SQL timing

**Files:**
- Create: `backend/src/main/java/com/antshorttv/observability/DatabaseQueryObservation.java`
- Create: `backend/src/main/java/com/antshorttv/observability/MybatisQueryTimingInterceptor.java`
- Create: `backend/src/main/java/com/antshorttv/observability/ProductionWorkspaceTimingFilter.java`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/antshorttv/observability/DatabaseQueryObservationTest.java`
- Create: `backend/src/test/java/com/antshorttv/observability/ProductionWorkspaceTimingFilterTest.java`

- [ ] **Step 1: Write failing collector and filter tests**

Assert query timings accumulate in request scope. Assert normalized route output contains no project ID, request headers, SQL text, parameters, content, Cookie or Authorization values.

- [ ] **Step 2: Verify RED**

Run: `mvn -f backend/pom.xml -Dtest=DatabaseQueryObservationTest,ProductionWorkspaceTimingFilterTest test`

Expected: FAIL because the observability components do not exist.

- [ ] **Step 3: Implement request-scoped collection**

Use a strictly cleared `ThreadLocal` around selected synchronous GET routes and time MyBatis `Executor.query` with the existing plugin API. Store only count and cumulative duration; do not retain SQL or parameter objects.

- [ ] **Step 4: Implement safe slow-request logging**

Log normalized route, HTTP status, total duration, SQL count and cumulative query duration at a configurable threshold. Mark connection wait as unavailable until separately measurable. Ensure `finally` clears the scope after success or exception.

- [ ] **Step 5: Verify GREEN**

Run: `mvn -f backend/pom.xml -Dtest=DatabaseQueryObservationTest,ProductionWorkspaceTimingFilterTest test`

Expected: PASS, including exception cleanup and sensitive-field absence assertions.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/antshorttv/observability backend/src/main/resources/application.yml backend/src/test/java/com/antshorttv/observability
git commit -m "feat(observability): trace workspace SQL timing"
```

### Task 6: Verify, deploy, and complete project 33 evidence

**Files:**
- Modify: `openspec/changes/split-production-workspace-apis/verification.md`
- Modify: `openspec/changes/split-production-workspace-apis/tasks.md`

- [ ] **Step 1: Run full verification**

Run:

```bash
mvn -f backend/pom.xml test
cd frontend && npm test && npm run lint && npx antd lint ./src && npm run build
```

Expected: zero failures; record pre-existing warnings separately.

- [ ] **Step 2: Deploy with rollback protection**

Follow `docs/antv-deployment-runbook.md`: push, verify backups and artifact hashes, atomically switch the release, restart, and run public health checks.

- [ ] **Step 3: Capture authenticated comparison**

Warm up once, then alternate legacy and focused calls five times for project 33. Record median request duration, payload bytes, SQL count and cumulative SQL duration. Verify on-demand bodies and bounded storyboard pages.

- [ ] **Step 4: Update OpenSpec evidence and checkboxes**

Write exact measurements and limitations into `verification.md`. Mark 7.2 complete only when authenticated evidence includes every required field.

- [ ] **Step 5: Commit verification evidence**

```bash
git add openspec/changes/split-production-workspace-apis
git commit -m "docs(workbench): record backend read performance"
```
