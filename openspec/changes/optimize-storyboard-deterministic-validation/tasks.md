## 1. Capture Regression Baseline

- [x] 1.1 Add source-segmenter regression tests for `出场人物：...`, colon-containing camera directions, known character dialogue, VO/OS markers, and unknown colon prefixes.
- [x] 1.2 Add storyboard-save regression tests that reproduce the `Then`/sequence-word rejection, missing sound ownership, out-of-range sound ownership, numbering gaps, and source coverage gap from the production batch.
- [x] 1.3 Add runner regression tests proving the current storyboard save failure produces a second business model invocation before changing the policy.

## 2. Implement Context-Aware Source Classification

- [x] 2.1 Add a typed segmentation context containing trusted current-script character names and explicit aliases, with normalization that preserves the original source text.
- [x] 2.2 Reorder `read_current_episode` data loading so the trusted speaker context is available before source segmentation without adding an Agent tool call.
- [x] 2.3 Implement structural-label and stage/camera-direction precedence, explicit VO/OS handling, known-speaker dialogue matching, and low-confidence classification warnings.
- [x] 2.4 Update source-segment serialization and focused tests to expose warnings while preserving stable IDs, offsets, types, and required-coverage semantics.

## 3. Define the Schema v3 Agent Contract

- [x] 3.1 Add failing Schema tests for v3 shot `sourceAnchor`, removal of required model-authored `soundSegmentIds`, and continued rejection of malformed or unknown fields.
- [x] 3.2 Update the `save_episode_storyboards` input Schema to accept the v3 contract while retaining compatibility parsing for historical v2 data.
- [x] 3.3 Update the storyboard Agent prompt and planning Skill to request source anchors, separate performance/emotion/camera content, and no sound-ID enumeration.
- [x] 3.4 Add bootstrap and migration-rehearsal tests proving only untouched built-in storyboard Agent/Skill definitions receive the v3 defaults and administrator-authored revisions remain unchanged.

## 4. Canonicalize Mechanical Storyboard Data

- [x] 4.1 Add failing tests for canonical storyboard numbering, per-storyboard shot numbering, derived adjacent starts, final required-segment extension, and idempotent normalization.
- [x] 4.2 Implement a focused storyboard normalizer that derives numbering and uniquely determined source boundaries before persistence.
- [x] 4.3 Keep unknown, reversed, or out-of-episode creative endpoints as structured hard failures and verify atomic rollback preserves the prior active storyboard set.

## 5. Derive Sound Ownership Deterministically

- [x] 5.1 Add failing tests for anchored sound assignment, omitted-anchor fallback, remaining-sound assignment, mixed non-audible segments, and exact-once ownership.
- [x] 5.2 Implement non-decreasing source-anchor normalization and duration-weighted fallback anchors within each canonical storyboard range.
- [x] 5.3 Derive final `soundSegmentIds`, `dialogue`, `narration`, and `innerOs` exclusively from trusted source segments and persist them in the existing downstream-compatible shot-plan shape.
- [x] 5.4 Add compatibility tests proving existing dubbing, lip-sync, subtitle, prompt, and video consumers can read v3-generated storyboards without changes.

## 6. Replace Subjective Rejection with Warnings

- [x] 6.1 Add failing tests proving sequence words and quoted `Then` do not block save, while technical duration and trusted-reference failures still do.
- [x] 6.2 Remove the `TOO_MANY_ACTIONS` pattern from save acceptance and produce structured action-density warnings with storyboard and shot positions.
- [x] 6.3 Update prompt rendering tests for separate performance, emotion, and camera fields without changing trusted dialogue or fixed media constraints.

## 7. Stop Implicit Business Correction

- [x] 7.1 Change the runner regression test to require exactly one business model call when `save_episode_storyboards` fails hard validation.
- [x] 7.2 Remove the storyboard-specific return-to-model correction branch while preserving asset, review, splitting, and provider transport retry behavior.
- [x] 7.3 Add execution and point-settlement tests proving storyboard business validation cannot create incremental reservation or a second call, and explicit user retry still creates a new execution.

## 8. Expose Diagnostics in the Workbench

- [x] 8.1 Extend storyboard save/execution responses with classification warnings, normalization counts, derived-sound counts, action warnings, business-call count, and technical-retry count.
- [x] 8.2 Regenerate frontend API clients through the existing OpenAPI workflow and do not hand-edit generated service files.
- [x] 8.3 Add workbench UI tests and implementation for successful-with-warning status, per-shot warning details, and separate batch success/warning/failure totals.
- [x] 8.4 Ensure warnings do not present or automatically invoke a paid retry action.

## 9. Replay, Verify, and Release

- [x] 9.1 Build a sanitized offline replay fixture from the project 26 first-round cases and verify it never contacts a provider.
- [x] 9.2 Replay the 57-episode baseline and record how the 18 action, 15 missing-sound, one sound-range, and one coverage case resolve under the new contract.
- [x] 9.3 Run focused backend tests, the full relevant backend suite, frontend unit/type/lint checks, and OpenSpec strict validation.
- [ ] 9.4 Deploy backend and frontend together using the production runbook with the current release retained as the rollback target.
- [ ] 9.5 Run one controlled production episode and verify one business model call, valid canonical coverage and sound ownership, unchanged downstream media consumption, and separately reported technical retries.
- [ ] 9.6 Record production acceptance evidence, observed point savings, release identity, and rollback identity in this change before marking it complete.
