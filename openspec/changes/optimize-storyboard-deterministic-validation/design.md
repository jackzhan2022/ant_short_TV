## Context

The storyboard Workflow Agent currently asks the model to generate a complete episode payload that includes storyboard and shot numbering, contiguous episode coverage, per-shot `soundSegmentIds`, durations, creative actions, asset references, and prompt details. `StoryboardToolDataService` validates the entire payload atomically. `WorkflowAgentRunner` sends the first `save_episode_storyboards` failure back to the model for one paid correction round, regardless of whether the error is correctable by another model response.

Production evidence from project 26 shows 35 correction rounds across 57 episodes: 18 were triggered by a keyword-based multiple-action check, 15 by missing sound ownership, one by an out-of-range sound, and one by a source gap. Samples also show that the source segmenter classifies structural lines such as `出场人物：...` and camera directions containing a colon as dialogue. The existing design therefore combines creative judgment with bookkeeping and then charges for using the model to repair deterministic or false-positive failures.

The change crosses source parsing, Agent input/output Schema, save validation, prompt rendering, execution policy, diagnostics, and the production workbench. Existing storyboard rows and downstream media consumers already depend on persisted sound IDs and injected source text, so compatibility must be preserved.

## Goals / Non-Goals

**Goals:**

- Make source classification conservative and context-aware so structural text is not treated as audible dialogue.
- Move numbering, contiguous boundary construction, and sound ownership from model bookkeeping to deterministic backend logic.
- Preserve stable sound IDs and trusted source text for dubbing, lip sync, subtitles, video generation, and audit.
- Treat subjective action density as a warning instead of a save failure.
- Prevent storyboard business validation failures from causing an implicit second model call.
- Keep historical Schema v2 storyboards and current downstream readers working.

**Non-Goals:**

- Changing the 10-to-15-second storyboard video-unit contract or the 1.5-to-4-second internal-shot duration contract.
- Rewriting plot, dialogue, character relationships, or creative shot choices in backend code.
- Removing bounded retries for provider transport failures.
- Backfilling or rewriting historical storyboard rows.
- Guaranteeing that every creatively weak shot is detected automatically.

## Decisions

### 1. Classify source lines with structural precedence and trusted speaker context

`read_current_episode` will load current-script character names and explicit aliases before segmentation and pass them to `EpisodeSourceSegmenter`. The segmenter will classify titles and structural labels first, stage/camera directions second, explicit VO/OS markers third, and known speakers fourth. A colon alone will no longer prove that a line is dialogue. Unknown colon-prefixed lines will remain required visual coverage but will be classified as action with a low-confidence warning.

This fixes the observed false sounds without relying on a growing blacklist alone. Keeping the current broad `prefix: value` matcher and adding only `出场人物` was rejected because the same defect recurs for camera, cast, time, location, and future structural labels. Asking the model to classify lines was rejected because parsing must be stable, auditable, and free of extra provider cost.

### 2. Introduce Schema v3 source anchors and derive sound ownership

Schema v3 removes model-authored `soundSegmentIds` and adds an optional ordered `sourceAnchor` to each internal shot. The anchor identifies the principal source location of the shot; it does not constrain visual reuse of a source action across different camera angles.

For each storyboard, the backend collects the trusted audible segments inside its final source range. It normalizes valid anchors to non-decreasing source ordinals and assigns each sound to the first shot whose anchor is at or after the sound; remaining sounds belong to the last shot. If anchors are omitted, it derives monotonic anchors from shot order and duration weights. The backend then persists the generated IDs and injects the original dialogue, narration, and inner-OS text using the existing output fields.

Retaining model-authored ID arrays was rejected because it preserves the exact dynamic-set bookkeeping that caused omissions. Assigning all sounds to the final shot was rejected because it would be complete but visibly wrong for lip sync. Requiring a strict source range for every shot was rejected because a visual action may legitimately be covered by multiple internal shots; one ordered anchor provides temporal guidance without imposing exclusive visual ownership.

### 3. Canonicalize mechanical fields before validation

Array order becomes authoritative for `storyboardNo` and `shotNo`; the backend writes canonical consecutive numbers. Each storyboard start is derived from the prior canonical end, and the final storyboard extends to the last required source segment. Model-provided end boundaries remain creative cut decisions but must resolve to known, non-decreasing required segments. Invalid or impossible end boundaries remain hard failures because silently choosing a different dramatic cut would change intent.

This approach prevents gaps, overlaps, and numbering failures by construction while retaining model control of meaningful boundaries. Silently repairing unknown or reversed endpoints was rejected because it would hide corrupted references and could move plot events between video units.

### 4. Separate hard invariants, normalization, and warnings

Hard failures are limited to trusted-scope violations, stale fingerprints, malformed required structure, unknown references, impossible source ordering, material ownership errors, and downstream duration limits. Mechanical discrepancies that have one safe canonical answer are normalized. Subjective quality findings, including sequence words or potentially dense action descriptions, become structured warnings attached to storyboard and shot positions.

The existing `TOO_MANY_ACTIONS` keyword pattern will not participate in save acceptance. It may be reused only as a warning signal. A semantic hard gate was rejected because neither the current regex nor another deterministic keyword list can reliably distinguish one continuous performance from multiple video actions.

### 5. End business validation without an implicit correction round

`WorkflowAgentRunner` will no longer return a failed `save_episode_storyboards` call to the model. A hard business failure ends the Run, preserves the prior active storyboard set, and exposes the structured diagnostic. Provider connection, throttling, and recoverable timeout handling remains in the invocation layer and is recorded separately as technical retry behavior.

This makes cost behavior predictable: one successful business generation normally means one provider call. Keeping one correction only for selected codes was rejected for the first release because deterministic normalization removes the known correctable cases, while any remaining hard failure requires new trusted input or an explicit user decision rather than another hidden paid attempt.

### 6. Preserve the persisted compatibility shape

Historical Schema v2 payloads remain readable. New Agent runs use Schema v3, but canonical persisted `shot_plan_json` continues to contain `soundSegmentIds`, `dialogue`, `narration`, and `innerOs`. Existing downstream media workflows therefore do not require a coordinated migration. Regenerating an episode naturally replaces it with v3-derived data; no history rewrite occurs.

### 7. Expose warnings and retry categories separately

The save result and execution diagnostics will record classification warnings, normalization counts, derived-sound counts, action-density warnings, hard failure codes, business model-call count, and provider technical retries. The workbench will present a successful result with warnings separately from a failed execution and will not label a warning as requiring paid retry.

### 8. Persist batch membership and derive summaries from execution truth

Storyboard batch generation will persist a batch header plus one immutable item per selected active episode. Each item stores the episode and the existing `ai_execution_task` created for that episode. The batch read model derives item state, business-call count, technical-retry count, and settled points from the linked execution instead of copying execution lifecycle fields into a second state machine. A succeeded item is classified as `SUCCESS_WITH_WARNING` when its active storyboard set for the linked run contains persisted warnings; otherwise it is `SUCCESS`.

The batch submission endpoint will enqueue every selected episode through the existing storyboard operation submission path using a batch-scoped, episode-specific idempotency key. The existing execution dispatcher remains the concurrency authority, so the configured four-worker limit applies without a second executor. An item failure does not cancel siblings and no warning creates a retry execution.

Client-only aggregation was rejected because a refresh loses failed execution membership and cannot distinguish an episode that was never submitted. Copying execution statuses and counters into batch rows was rejected because asynchronous updates can leave duplicated lifecycle state inconsistent with point settlement. A separate batch worker pool was rejected because it would bypass the existing claim, retry, concurrency, and billing controls.

## Risks / Trade-offs

- **[Unknown real speaker is classified as action]** → Emit a low-confidence classification warning and use current character names plus aliases; users can add missing formal assets before regeneration.
- **[Derived sound is assigned to an adjacent shot]** → Use ordered source anchors, preserve trusted source order, and validate with real lip-sync samples before rollout.
- **[Removing the action hard gate allows an overloaded shot]** → Keep a visible quality warning and separate performance, emotion, and camera fields in the model contract; never convert the warning into an implicit paid call.
- **[Schema v3 prompt changes alter model output quality]** → Retain v2 readers, add strict Schema tests, and perform offline replay plus a controlled production smoke test.
- **[Canonical boundary construction hides a model omission]** → Normalize only fields with a unique answer; reject unknown or reversed creative endpoints without correction.
- **[Operators confuse technical retry with business correction]** → Report the two counters independently in execution diagnostics and batch summaries.

## Migration Plan

1. Add regression tests for production classification and keyword false-positive samples.
2. Add speaker-context segmentation and warning output without changing saved storyboard behavior.
3. Add Schema v3, source-anchor normalization, sound derivation, canonical numbering, and compatibility tests.
4. Change action-density rejection to warnings and stop storyboard business correction in the runner.
5. Update the workbench warning presentation and generated API types through the normal OpenAPI workflow.
6. Replay the 57 captured first-round payloads offline without provider calls and compare canonical outputs.
7. Deploy backend and frontend together, run one controlled episode, verify one business call and downstream sound/media consumption, then enable batch use.

Rollback switches the current release symlink to the previous known-good release. Because the migration does not rewrite historical rows and persists the compatibility sound shape, rollback does not require a database data rollback. Any new v3-only request is confined to the generation boundary and does not become a new downstream requirement.

## Open Questions

None. The user selected precise source-guided sound ownership, preservation of backend integrity checks, warning-only subjective quality checks, and no implicit business correction.
