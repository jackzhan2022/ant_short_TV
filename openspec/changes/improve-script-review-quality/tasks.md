> 2026-09-12 替代声明：`remove-legacy-ai-workflow-paths` 是最终目标。结构化候选、语义裁决、异常门禁、旧工具和 feature flags 条款已被唯一 Markdown 审核替代；保留维度/范围冻结、成功单元复用、取消、日志与缓存观测。 原勾选和验证记录为历史证据，不据此恢复旧链路。归档时先合并仍有效的基础能力，最后合并 remove-legacy-ai-workflow-paths；之后不得再导入这些已退役条款。

## 1. Baseline and regression tests

> Current external constraint (confirmed 2026-09-05): the human-reviewed list of 30+ issues
> and its exact script-version binding are not available. Tasks 1.3, 1.4, 8.2, the
> human-quality threshold in 8.5, and the human benchmark portion of 8.6 must remain
> unchecked. Synthetic fixtures may verify mechanics and history isolation, but must not
> be presented as measured review quality or used as model input.

- [x] 1.1 Add a regression test proving that an unresolved issue omitted by a new review of the identical version cannot become `fixed`.
- [x] 1.2 Add a regression test proving that invalid unit candidates are reported or audited instead of being silently removed from a successful empty result.
- [ ] 1.3 Convert the available human-reviewed issue list into a version-bound evaluation fixture with expected dimensions and evidence locations.
- [ ] 1.4 Record baseline candidate recall, formal-report recall, false positives, history-isolation accuracy, latency, and token usage for the existing DEEP flow.

## 2. Persistence model and migrations

- [x] 2.1 Add schema and entities for per-dimension review stages, attempts, terminal status, frozen hashes, Run references, coverage, and errors.
- [x] 2.2 Add immutable candidate audit and semantic-decision persistence with candidate lineage, status, confidence, rationale, severity decision, and evidence references.
- [x] 2.3 Extend AI call and usage persistence for cache-read tokens, cache-write tokens, cache-observability state, and non-secret prompt-cache identity.
- [x] 2.4 Add effective price component support for ordinary input, cached input, and cache writes without changing historical priced usage.
- [x] 2.5 Add migration and repository tests covering existing tasks, nullable historical cache metrics, indexes, uniqueness, and transactional rollback.

## 3. GPT cache request and usage support

- [x] 3.1 Extend text request contracts with provider-capability-gated prompt cache key and cache options while preserving existing compatible providers.
- [x] 3.2 Build a deterministic review cache identity from tenant isolation, model, version hash, rules, tool contract, and structure index while excluding all history.
- [x] 3.3 Serialize shared rules, tools, structure, and frozen script in a stable prefix, append dimension instructions after the reusable boundary, and exclude all history.
- [x] 3.4 Parse provider cache-read and cache-write usage details, preserving unknown when the gateway omits them.
- [x] 3.5 Record ordinary input, cached input, cache-write, output, latency, and cache hit ratio in call logs and immutable usage lines.
- [x] 3.6 Add adapter tests for cache hit responses, missing cache details, rejected cache options, retry behavior, and no double-counting of input tokens.

## 4. Per-dimension deep-review orchestration

- [x] 4.1 Replace mixed-dimension DEEP child planning with one independently persisted discovery stage per selected dimension.
- [x] 4.2 Add dimension-specific prompts and mandatory checklists for plot, dialogue, relationships, knowledge boundaries, timeline, props, visual execution, and suspense/reversal.
- [x] 4.3 Add cache warm-up and bounded dimension concurrency so all Runs share the frozen common prefix without creating duplicate terminal stages.
- [x] 4.4 Make retry schedule only failed, missing, or stale dimension stages and their unfinished dependents while retaining successful results.
- [x] 4.5 Expose per-dimension progress, failures, attempts, and retry state through review-task responses.
- [x] 4.6 Add coordinator tests for complete success, one-dimension failure, retry, cancellation, stale snapshots, cache identity changes, and idempotent aggregation.

## 5. Candidate validation and semantic quality review

- [x] 5.1 Change candidate saving to reject an atomic invalid submission with candidate-specific errors or persist an explicit invalid audit record; remove silent candidate dropping.
- [x] 5.2 Add phase-scoped tools for reading frozen candidates and bounded supporting source text and for saving one terminal semantic decision per candidate.
- [x] 5.3 Implement the semantic quality Run that evaluates evidence support, rule applicability, alternative explanations, severity, suggestion effectiveness, and duplicates.
- [x] 5.4 Preserve `CONFIRMED`, `NEEDS_HUMAN_REVIEW`, `REJECTED`, and `INSUFFICIENT_EVIDENCE` decisions with confidence and rationale without modifying original candidates.
- [x] 5.5 Add tests for confirmed findings, disputed interpretations, insufficient evidence, malformed candidates, severity adjustment, duplicate clustering, and transactional failures.

## 6. History isolation and formal aggregation

- [x] 6.1 Add regression tests proving historical issues cannot affect cache identity, prompts, tools, semantic decisions, anomaly gates, or aggregation.
- [x] 6.2 Remove historical issue reads and automatic cross-round matching from every new review phase while preserving old reports for manual viewing.
- [x] 6.3 Save every new report independently without creating `fixed`, `pending_recheck`, `uncertain`, or other cross-round lifecycle events.
- [x] 6.4 Allow quality and aggregation Runs to retrieve bounded source content across any in-scope unit for cross-episode and cross-dimension verification.
- [x] 6.5 Make the zero-problem quality gate depend only on current-run coverage and current frozen evidence, and prevent an unconditional 100 score until it succeeds.
- [x] 6.6 Update formal aggregation to consume only current semantic decisions and evidence, preserve multi-dimension evidence, expose human-review findings, and save exactly once atomically.
- [x] 6.7 Add end-to-end tests for history isolation, cross-unit contradictions, zero findings, anomaly review, aggregation retry, and historical report read compatibility.

## 7. API and workbench observability

- [x] 7.1 Extend backend response contracts with dimension progress, quality state, candidate decision counts, cache usage, latency, and aggregate cache hit ratio.
- [x] 7.2 Update the review workbench to display dimension stages, isolated retry actions, semantic statuses, anomaly gates, and cache metrics without exposing prompts or source text in logs.
- [x] 7.3 Update AI usage and cost views to distinguish ordinary input, cache reads, cache writes, output, unknown cache detail, and incomplete price configuration.
- [x] 7.4 Add controller and frontend tests for loading, partial progress, failed dimensions, human-review findings, missing cache detail, and historical report compatibility.

## 8. Evaluation and rollout

- [x] 8.1 Implement a non-production evaluation runner that compares review findings with version-bound human benchmarks and reports per-dimension misses and extras.
- [ ] 8.2 Run the old and new flows against the human-reviewed script and tune checklists using measured recall and false positives without weakening evidence requirements.
- [x] 8.3 Add feature flags for cache observability, dimensional orchestration, semantic review, and anomaly gating, with safe defaults and documented rollback behavior.
- [ ] 8.4 Deploy cache observability first, verify live `cached_tokens` against the 8,960-of-9,631 test baseline, and confirm billing reconciliation.
- [ ] 8.5 Shadow-run the new review pipeline, confirm history isolation and acceptable quality/cost thresholds, then make it the default DEEP flow.
- [ ] 8.6 Run backend tests, frontend type checking, Biome, Jest, Ant Design lint, production build, migration rehearsal, and the human benchmark suite before release.
