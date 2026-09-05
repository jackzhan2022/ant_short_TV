# Storyboard Context Pruning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce the storyboard planning model input below 30,000 characters for project 26 while preserving the complete current episode, trusted source segments, continuity summaries, and valid material bindings.

**Architecture:** Keep the five trusted preparation tools and their audit records unchanged. Add bounded adjacent-episode excerpts at the data-service boundary, then pass the combined tool results through a pure `StoryboardContextReducer` immediately before the planning model call. The reducer produces a priority-ordered compact projection and metrics; `WorkflowAgentRunner` logs counts and sends only the compact projection to the model.

**Tech Stack:** Java 21, Spring Boot, Jackson `JsonNode`, JUnit 5, AssertJ, Mockito, Maven, OpenSpec.

---

## File Structure

- Create `backend/src/main/java/com/antshorttv/workflowagent/run/StoryboardContextReducer.java`: pure storyboard-only context projection, budget enforcement, and reduction metrics.
- Create `backend/src/test/java/com/antshorttv/workflowagent/run/StoryboardContextReducerTest.java`: focused RED/GREEN coverage for projection, relevance, fallback, malformed data, and budget priority.
- Modify `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java`: add bounded Unicode-safe beginning/ending excerpts to adjacent episode summaries.
- Modify `backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataServiceTest.java`: prove adjacent full scripts are not returned and excerpts are bounded.
- Modify `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java`: inject the reducer, log size metrics, and send reduced context to the planning model.
- Modify `backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java`: prove raw audited outputs remain unchanged while the model receives reduced content.
- Modify `openspec/changes/add-storyboard-workflow-agent/specs/short-drama-storyboard-agent/spec.md`: add the model-context minimization requirement and scenarios.
- Modify `openspec/changes/add-storyboard-workflow-agent/tasks.md`: add and track context-pruning implementation, verification, deployment, and production evidence.

### Task 1: Add bounded adjacent-episode continuity excerpts

**Files:**
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataServiceTest.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java`

- [x] **Step 1: Write the failing adjacent-context test**

Add a test that inserts long previous and next episode bodies around the selected episode, calls `readAdjacentEpisodes`, and asserts the response contains identity, title, summary, `openingExcerpt`, `endingExcerpt`, and `contentTruncated`, but no `content` field.

```java
@Test
void adjacentEpisodesExposeOnlyBoundedContinuityExcerpts() {
    insertAdjacentEpisode(1, "Previous", "previous summary", "前".repeat(900) + "PREVIOUS_END");
    insertAdjacentEpisode(3, "Next", "next summary", "NEXT_START" + "后".repeat(900));

    JsonNode adjacent = service.readAdjacentEpisodes(context);

    assertThat(adjacent.path("previous").has("content")).isFalse();
    assertThat(adjacent.path("previous").path("endingExcerpt").asText()).endsWith("PREVIOUS_END");
    assertThat(adjacent.path("next").path("openingExcerpt").asText()).startsWith("NEXT_START");
    assertThat(adjacent.path("previous").path("contentTruncated").asBoolean()).isTrue();
    assertThat(adjacent.path("next").path("contentTruncated").asBoolean()).isTrue();
    assertThat(adjacent.path("previous").path("endingExcerpt").asText()).hasSizeLessThanOrEqualTo(600);
    assertThat(adjacent.path("next").path("openingExcerpt").asText()).hasSizeLessThanOrEqualTo(600);
}
```

- [x] **Step 2: Run the test and verify RED**

Run:

```powershell
cd backend
mvn -q -Dtest=ScreenplayToolDataServiceTest#adjacentEpisodesExposeOnlyBoundedContinuityExcerpts test
```

Expected: FAIL because the adjacent response does not yet contain excerpt fields.

- [x] **Step 3: Implement Unicode-safe excerpts**

Add a 600-code-point bound and helper methods. `adjacentSummary` must add both excerpts so previous and next records have a stable shape, while `contentTruncated` states whether any content was omitted.

```java
private static final int ADJACENT_EXCERPT_CODE_POINTS = 600;

private ObjectNode adjacentSummary(Map<String, Object> row, boolean previous) {
    ObjectNode item = episodeSummary(row);
    String content = String.valueOf(row.getOrDefault("content", ""));
    put(item, previous ? "endingSummary" : "openingSummary", row.get("summary"));
    item.put("openingExcerpt", prefixByCodePoints(content, ADJACENT_EXCERPT_CODE_POINTS));
    item.put("endingExcerpt", suffixByCodePoints(content, ADJACENT_EXCERPT_CODE_POINTS));
    item.put("contentTruncated",
        content.codePointCount(0, content.length()) > ADJACENT_EXCERPT_CODE_POINTS);
    return item;
}
```

Implement `prefixByCodePoints` and `suffixByCodePoints` with `offsetByCodePoints` so surrogate pairs cannot be split.

- [x] **Step 4: Run the focused service test**

Run:

```powershell
cd backend
mvn -q -Dtest=ScreenplayToolDataServiceTest test
```

Expected: all `ScreenplayToolDataServiceTest` tests PASS.

- [x] **Step 5: Commit Task 1**

```powershell
git add backend/src/main/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataService.java backend/src/test/java/com/antshorttv/workflowagent/tool/ScreenplayToolDataServiceTest.java
git commit -m "feat(storyboard): bound adjacent episode context"
```

### Task 2: Build the storyboard-only context reducer

**Files:**
- Create: `backend/src/test/java/com/antshorttv/workflowagent/run/StoryboardContextReducerTest.java`
- Create: `backend/src/main/java/com/antshorttv/workflowagent/run/StoryboardContextReducer.java`

- [x] **Step 1: Write failing projection tests**

Create tests around this public contract:

```java
StoryboardContextReducer.Reduction result = reducer.reduce(prepared);
assertThat(result.context().path("read_current_episode").path("content"))
    .isEqualTo(currentEpisode.path("content"));
assertThat(result.context().path("read_current_episode").path("sourceSegments"))
    .isEqualTo(currentEpisode.path("sourceSegments"));
assertThat(result.context().toString()).doesNotContain("raw_response", "FULL_PROJECT_SCRIPT");
assertThat(result.originalCharacters()).isGreaterThan(result.reducedCharacters());
```

Fixtures must include:

- a complete current episode with source segments and asset catalog;
- previous/next summaries and excerpts;
- `globalUnderstanding`;
- an `EPISODE_SPLITTING` stage whose normalized JSON contains a large `content` field;
- an `EPISODE_SUMMARY` stage containing multiple episode entries;
- verbose matching and nonmatching assets.

Assert that only the selected episode summary remains, relevant assets retain `assetKey` and `variantKey`, nonmatching assets are absent when matches exist, and current content/source segments are structurally equal to the input. Do not require the verbose input `assetCatalog` to remain structurally equal because it is intentionally projected.

- [x] **Step 2: Run projection tests and verify RED**

Run:

```powershell
cd backend
mvn -q -Dtest=StoryboardContextReducerTest test
```

Expected: test compilation FAILS because `StoryboardContextReducer` does not exist.

- [x] **Step 3: Implement the projection and relevance rules**

Create the component with a pure reduction result:

```java
@Component
public final class StoryboardContextReducer {
    static final int SOFT_CHARACTER_BUDGET = 30_000;

    private final ObjectMapper json;

    public StoryboardContextReducer(ObjectMapper json) {
        this.json = json;
    }

    public Reduction reduce(ObjectNode prepared) {
        int originalCharacters = serializedLength(prepared);
        ObjectNode reduced = json.createObjectNode();
        JsonNode current = prepared.path("read_current_episode");
        reduced.set("read_current_episode", current.deepCopy());
        // Add compact project, adjacent, analysis, and asset projections in priority order.
        enforceSoftBudget(reduced);
        return metrics(prepared, reduced, originalCharacters);
    }

    public record Reduction(
        ObjectNode context,
        int originalCharacters,
        int reducedCharacters,
        int adjacentEpisodeCount,
        int characterCount,
        int sceneCount,
        int propCount,
        boolean optionalSectionsDropped
    ) {}
}
```

The implementation must parse textual `normalized_json` defensively, recursively reject full-text keys (`content`, `script`, `screenplay`, `raw_response`, `rawResponse`), choose the entry whose `episodeNo` equals the current episode number, and keep an identity-only asset fallback when name/alias matching finds no entries.

- [x] **Step 4: Add failing malformed-data and budget-priority tests**

Assert malformed normalized JSON is omitted without an exception. Construct optional sections over 30,000 characters and assert the reducer removes optional data before touching the complete current episode. Construct a required current episode over 30,000 characters and assert it remains complete while all optional sections are dropped.

- [x] **Step 5: Implement safe degradation and budget enforcement**

Implement optional-section assembly in priority order. Measure the serialized result after each optional addition; if the budget would be exceeded, remove or compact that optional section. Never mutate the input tree, never shorten `read_current_episode.content`, and never change `sourceSegments`.

- [x] **Step 6: Run reducer tests and verify GREEN**

Run:

```powershell
cd backend
mvn -q -Dtest=StoryboardContextReducerTest test
```

Expected: all reducer tests PASS with no test errors.

- [x] **Step 7: Commit Task 2**

```powershell
git add backend/src/main/java/com/antshorttv/workflowagent/run/StoryboardContextReducer.java backend/src/test/java/com/antshorttv/workflowagent/run/StoryboardContextReducerTest.java
git commit -m "feat(storyboard): prune planning context"
```

### Task 3: Integrate reduction into the audited runner

**Files:**
- Modify: `backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java`
- Modify: `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java`

- [x] **Step 1: Write the failing runner integration test**

Change the storyboard preparation fixture so `read_script_analysis` returns a unique `FULL_PROJECT_SCRIPT` marker while `read_current_episode` returns a unique `CURRENT_EPISODE_TEXT` marker. Capture the single planning request and assert:

```java
String planningMessage = requests.getValue().textRequest().messages().stream()
    .map(AiChatMessage::content)
    .filter(Objects::nonNull)
    .collect(Collectors.joining("\n"));
assertThat(planningMessage)
    .contains("CURRENT_EPISODE_TEXT")
    .doesNotContain("FULL_PROJECT_SCRIPT", "raw_response");
```

Keep existing verifications proving all five original tool outputs are recorded through `recordToolStep` before the model call.

- [x] **Step 2: Run the runner test and verify RED**

Run:

```powershell
cd backend
mvn -q -Dtest=WorkflowAgentRunnerTest#storyboardAgentHostPreparesAllReadsBeforeOnePlanningModelCall test
```

Expected: FAIL because the planning message still contains the full script marker.

- [x] **Step 3: Inject and call the reducer**

Add `StoryboardContextReducer` to the autowired constructor and keep the compatibility constructor used by tests by constructing a reducer from the existing `ObjectMapper`. In `prepareStoryboardContext`, reduce only after all five tool steps have been validated, recorded, and copied.

```java
StoryboardContextReducer.Reduction reduction = storyboardContextReducer.reduce(prepared);
LOG.info(
    "Storyboard context reduced runId={} episodeId={} originalChars={} reducedChars={} adjacent={} characters={} scenes={} props={} optionalDropped={}",
    runId, input.episodeId(), reduction.originalCharacters(), reduction.reducedCharacters(),
    reduction.adjacentEpisodeCount(), reduction.characterCount(), reduction.sceneCount(),
    reduction.propCount(), reduction.optionalSectionsDropped());
messages.add(AiChatMessage.user(STORYBOARD_CONTEXT_INSTRUCTION + writeJson(reduction.context())));
```

Do not log any JSON content.

- [x] **Step 4: Run runner and reducer regression tests**

Run:

```powershell
cd backend
mvn -q -Dtest=WorkflowAgentRunnerTest,StoryboardContextReducerTest test
```

Expected: both test classes PASS; the storyboard flow still invokes the model once and audits five reads.

- [x] **Step 5: Commit Task 3**

```powershell
git add backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunner.java backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunnerTest.java
git commit -m "feat(storyboard): send reduced planning context"
```

### Task 4: Record the contract and verify the release candidate

**Files:**
- Modify: `openspec/changes/add-storyboard-workflow-agent/specs/short-drama-storyboard-agent/spec.md`
- Modify: `openspec/changes/add-storyboard-workflow-agent/tasks.md`

- [x] **Step 1: Add OpenSpec scenarios and tasks**

Add a requirement that storyboard planning receives the complete current episode but no full-project script or raw analysis response. Add scenarios for compact adjacent continuity, relevance-filtered assets, safe optional-data degradation, and required-data-over-budget behavior. Add Task 10 checkboxes matching Tasks 1–4 in this plan.

- [x] **Step 2: Validate OpenSpec strictly**

Run:

```powershell
openspec validate add-storyboard-workflow-agent --strict
```

Expected: `Change 'add-storyboard-workflow-agent' is valid`.

- [x] **Step 3: Run focused backend verification**

Run:

```powershell
cd backend
mvn -q -Dtest=ScreenplayToolDataServiceTest,StoryboardContextReducerTest,WorkflowAgentRunnerTest,StoryboardAgentRunContractTest test
```

Expected: 0 failures and 0 errors.

- [x] **Step 4: Run the complete backend suite**

Run:

```powershell
cd backend
mvn -q test
```

Expected: 0 failures and 0 errors; the existing intentional skip remains the only skipped test.

- [x] **Step 5: Commit OpenSpec and verification evidence**

```powershell
git add openspec/changes/add-storyboard-workflow-agent/specs/storyboard-workflow-agent/spec.md openspec/changes/add-storyboard-workflow-agent/tasks.md
git commit -m "docs(openspec): specify storyboard context pruning"
```

- [x] **Step 6: Deploy using the existing runbook**

Follow `docs/antv-deployment-runbook.md`: build the backend artifact, create a timestamped release under `/opt/antv/releases`, preserve the previous release as the rollback target, back up the database and workflow skills, switch `/opt/antv/current`, restart `antv.service`, and verify the service, homepage, feature flag, and Flyway state.

- [x] **Step 7: Perform production acceptance on project 26**

Generate the selected episode's storyboards once. Verify the run succeeds with one planning call, valid source coverage, material bindings, usage settlement, and no correction caused by pruning. Read the structured reduction log and compare planning input size, model duration, and total execution duration with execution 91399 (`~220,000` prepared characters, `107,899 ms` model time, `110 s` total).

- [x] **Step 8: Record final production evidence**

Update Task 10 with the release ID, rollback target, reduced character count, model call count, model duration, total duration, storyboard count, and settlement result. Run strict OpenSpec validation again, commit the evidence, and push `master`.

Production acceptance completed on release `202609051124-8007ee8`. Project 26 execution `94278` / Run `477` reduced 240,260 prepared characters to 14,123, made one 37,236 ms model call, completed in 39 seconds, atomically saved four `BOUND` storyboards with contiguous `S0003`–`S0042` coverage and three internal shots each, and settled 10 points. The known-good rollback target is `202609050453-2f8710c`.
