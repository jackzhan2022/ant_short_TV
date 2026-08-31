## 1. Baseline contracts and migration design

- [x] 1.1 Add failing schema tests for review snapshot, unit, candidate-result, hash, Agent Run reference, status, retry, and lookup columns and indexes.
- [x] 1.2 Add an additive migration for `review_fanout_snapshot`, `review_fanout_unit`, and `review_unit_result`, plus compatible `review_task` workflow reference and hash fields.
- [x] 1.3 Add migration rehearsal tests proving existing review projects, versions, tasks, formal issues, hits, events, repairs, and exports remain readable without backfilling historical candidates.
- [x] 1.4 Add repository entities, mappers, and status enums for snapshots, units, candidates, and formal Agent Run references.
- [x] 1.5 Add repository tests for idempotent snapshot attempts, unit uniqueness, candidate replacement, stable ordering, hash lookup, and concurrent updates.

## 2. Review Skill package and dynamic dimension catalog

- [x] 2.1 Add failing file-Skill contract tests for two common Skills, thirteen dimension Skills, and the cross-episode synthesis Skill.
- [x] 2.2 Create `script-review-foundation` with trusted-source, evidence, severity, uncertainty, scope, stale-input, and terminal-save rules.
- [x] 2.3 Create `script-review-execution-framework` with QUICK, DEEP child, DEEP aggregation, coverage, deduplication, and completion behavior.
- [x] 2.4 Create the thirteen dimension Skills using one shared structure for checks, non-issues, evidence requirements, severity guidance, and actionable suggestions.
- [x] 2.5 Create `script-review-cross-episode-synthesis` for cross-unit identity, timeline, scene, prop, visual, emotion, causal, suspense, reversal, and foreshadowing analysis.
- [x] 2.6 Add behavior fixtures covering valid issues, false positives, uncertain evidence, multi-hit aggregation, dimension isolation, and cross-episode findings.
- [x] 2.7 Implement a server-owned review-dimension enum and exact dimension-to-Skill mapping shared by task validation and execution-plan composition.
- [x] 2.8 Add tests proving one selected dimension loads only its Skill, multiple dimensions load exactly their Skills, ordering is deterministic, and unknown client Skill codes cannot be injected.

## 3. Tool schemas, catalog registration, and trusted scope

- [x] 3.1 Add failing tool-catalog tests for the six unique review tool codes and phase-specific maximum schemas.
- [x] 3.2 Define bounded strict input and output schemas for `read_review_context`, `read_review_content`, and `read_review_issue_history`.
- [x] 3.3 Define bounded strict input and output schemas for `save_review_unit_result`, `read_review_unit_results`, and `save_review_result`.
- [x] 3.4 Register all six tools in the workflow tool catalog and expose their descriptions in Agent (New) management.
- [x] 3.5 Extend `WorkflowAgentScopeGuard` and tool execution context with trusted review task, version, snapshot, unit, attempt, and phase scope.
- [x] 3.6 Add scope tests rejecting model-supplied business IDs, foreign tenants/projects/versions/tasks/units, out-of-phase tools, and unselected dimensions.
- [x] 3.7 Add payload and audit redaction tests proving tool logs remain bounded and do not duplicate complete long-script content.

## 4. Trusted review reads and scope planning

- [x] 4.1 Extract current review configuration, version, history, scope, and index reads behind a review tool data service without changing existing API behavior.
- [x] 4.2 Implement immutable content hashing, normalized scope hashing, selected-dimension hashing, and stable snapshot keys.
- [x] 4.3 Implement `read_review_context` with frozen mode, round, dimensions, scope, hashes, structural counts, phase, unit, and coverage metadata.
- [x] 4.4 Add a trusted structure index with stable episode, scene, line, paragraph, character-offset, and anchor metadata while preserving imported source text.
- [x] 4.5 Correct SCENES scope filtering and add tests proving ALL, EPISODES, and SCENES expose only the configured content.
- [x] 4.6 Implement `read_review_content` for bounded exact scope and unit reads, including heading-free offset units, pagination, and fingerprints.
- [x] 4.7 Implement `read_review_issue_history` with dimension/scope filtering, manual-resolution events, stable hit anchors, pagination, and empty first-round behavior.
- [x] 4.8 Add QUICK request-budget preflight and `REVIEW_SCOPE_TOO_LARGE_FOR_QUICK` behavior with actionable narrow-scope or DEEP guidance.

## 5. Candidate and formal-save transactions

- [x] 5.1 Add failing unit-save tests for phase, hash, dimension, coverage, size, location, excerpt, anchor, idempotency, and whole-payload rollback rules.
- [x] 5.2 Implement `save_review_unit_result` as one current candidate document per snapshot unit and child Run without creating formal issues.
- [x] 5.3 Implement `read_review_unit_results` with aggregation-only authorization, unchanged-snapshot validation, complete coverage enforcement, ordering, and bounded candidate output.
- [x] 5.4 Expand deterministic issue matching to use normalized dimensions, stable anchors, multiple hits, prior status, manual-resolution history, ambiguity detection, and explicit `uncertain` outcomes.
- [x] 5.5 Add matching tests for `new`, `persists`, `shifted`, `fixed`, `uncertain`, reopened manual resolutions, duplicate candidates, and ambiguous prior matches.
- [x] 5.6 Add failing final-save tests for score, conclusion, selected dimension, severity, required fields, exact evidence, location scope, multi-hit structure, duplicate identity, and stale hashes.
- [x] 5.7 Implement `save_review_result` as the only formal terminal save with server-assigned issue numbers and deterministic lifecycle statuses.
- [x] 5.8 Wrap task result, formal issue, hit, event, terminal Run reference, and completion updates in one transaction and add injected-failure rollback tests.
- [x] 5.9 Reject incomplete DEEP snapshots and preserve all prior formal rounds when validation or persistence fails.

## 6. Workflow Agent definition and Run policies

- [x] 6.1 Add failing bootstrap tests for an enabled `script-review` workflow Agent with model tool-calling compatibility, common Skills, maximum approved dimension Skills, and six-tool maximum allowlist.
- [x] 6.2 Implement idempotent review Agent bootstrap and environment flags while leaving direct legacy dispatch available for rollback.
- [x] 6.3 Implement the trusted review execution-plan factory that freezes Agent, ordered dynamic Skills, model, mode, scope, dimensions, hashes, phase, and narrowed tool allowlist.
- [x] 6.4 Add QUICK Run-policy tests requiring ordered trusted reads and exactly one successful `save_review_result` terminal action.
- [x] 6.5 Add DEEP child-policy tests requiring `save_review_unit_result` and forbidding unit-result reads or formal final save.
- [x] 6.6 Add DEEP aggregation-policy tests requiring complete unit-result reads and exactly one successful `save_review_result`.
- [x] 6.7 Add runner tests for missing saves, duplicate successful saves, invalid tool order, stale reads, correction after validation errors, cancellation, timeout, and bounded audit snapshots.

## 7. QUICK review adapter

- [x] 7.1 Add failing service tests proving QUICK dispatch uses the workflow Agent instead of direct `AiInvocationService.invokeText`.
- [x] 7.2 Implement the QUICK adapter from `review_task` execution to one frozen workflow Agent Run and persist its Run reference and current action.
- [x] 7.3 Map Agent and tool failures to stable review task error codes without converting partial or diagnostic model text into formal results.
- [x] 7.4 Preserve unified AI execution, idempotency, reservation, call audit, actual-usage settlement, cancellation, and whole-task retry behavior for QUICK.
- [x] 7.5 Add compatibility tests proving existing review task, detail, issue, history, repair, rollback, and export APIs continue to read QUICK formal results.

## 8. DEEP review unit planning and fan-out

- [x] 8.1 Add failing planner tests for selected episodes, selected scenes, complete scope, heading-free scripts, repeated text, Unicode offsets, stable fingerprints, bounded sizes, and overlap.
- [x] 8.2 Implement the deterministic review unit planner without creating or modifying formal episode records.
- [x] 8.3 Implement snapshot creation that freezes version, scope, dimensions, unit set, Agent, Skills, model, attempt, and concurrency.
- [x] 8.4 Add failing coordinator tests for bounded concurrency, one child per unit, candidate-save coverage, persisted progress, reload restoration, and cancellation.
- [x] 8.5 Implement the DEEP child coordinator and monotonic parent task progress using persisted snapshot and unit state.
- [x] 8.6 Add targeted-retry tests for one failed unit, missing candidate coverage, stale unit fingerprints, changed dimensions, changed scope, and explicit full regeneration.
- [x] 8.7 Implement reuse of only matching successful units and new child Runs for failed, missing, or stale units.
- [x] 8.8 Add aggregation tests proving complete-only startup, one aggregation Run per attempt, child-candidate reuse after aggregation failure, cross-unit deduplication, and no premature formal save.
- [x] 8.9 Implement the aggregation coordinator and complete the review task only after its terminal formal save succeeds.
- [x] 8.10 Correlate all child and aggregation calls with the parent execution and settle aggregated actual usage idempotently across retries and partial failures.

## 9. API and workbench integration

- [x] 9.1 Extend review task responses with workflow Agent references, frozen mode, snapshot, total/completed/failed units, current unit, unit statuses, retryability, stale state, and aggregation status.
- [x] 9.2 Update retry endpoints to distinguish QUICK whole-attempt retry, DEEP failed-unit retry, aggregation-only retry, and explicit full regeneration while preserving authorization.
- [x] 9.3 Update cancellation handling to stop pending DEEP scheduling, preserve successful candidates and audit records, and prevent late finalization.
- [x] 9.4 Update hand-written frontend review service types and requests without editing generated service files.
- [x] 9.5 Update the review workbench to render Agent actions, QUICK progress, DEEP unit progress, partial failures, current unit, aggregation, stale state, and targeted retry controls.
- [x] 9.6 Display selected dimension Skills and explicit QUICK size guidance without exposing arbitrary Skill or tool selection to users.
- [x] 9.7 Preserve current formal issue, multi-hit, manual resolution, repair, version history, rollback, and export UI behavior.
- [x] 9.8 Add frontend tests for single-dimension QUICK, multi-dimension QUICK, scene scope, DEEP progress restoration, one-unit retry, aggregation retry, stale state, and formal completed rendering.

## 10. Verification, rollout, and rollback

- [x] 10.1 Run focused migration, repository, Skill, tool schema, scope, read, save, matcher, Agent runner, QUICK adapter, fan-out, aggregation, controller, billing, and frontend test suites.
- [x] 10.2 Add end-to-end QUICK coverage proving dynamic one-dimension Skill loading, trusted scene scope, history matching, atomic formal save, and existing workbench reads.
- [x] 10.3 Add end-to-end DEEP coverage proving heading-free unit planning, bounded child Runs, persisted progress, targeted retry, cross-unit synthesis, multi-hit aggregation, and complete-only formal save.
- [x] 10.4 Add failure end-to-end coverage for stale hashes, foreign scope, absent evidence, invalid dimension/severity, one failed child, aggregation failure, transaction rollback, cancellation, and no premature completion.
- [x] 10.5 Run the full backend suite plus frontend type-check, Jest, Biome, and Ant Design lint required by the repository.
- [x] 10.6 Document Skill and tool catalogs, environment flags, safe QUICK budget, DEEP unit sizing, concurrency, retry, formal table ownership, status/error codes, billing, metrics, rollout order, and rollback.
- [x] 10.7 Bootstrap the review Agent and Skills in a non-production environment, smoke-test QUICK and DEEP sequentially, and verify Agent (New) management visibility.
- [x] 10.8 Enable production QUICK behind the workflow flag, monitor duplicate dispatch, invalid saves, latency, usage, and stale errors, then enable DEEP fan-out and aggregation.
- [x] 10.9 Verify rollback to direct legacy dispatch for new tasks while existing workflow task reports, candidates, audit records, and formal results remain readable.
