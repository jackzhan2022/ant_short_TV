# Scoped query cache Implementation Plan

> Use executing-plans to implement task by task. The user approved scoped invalidation and longer TTLs in this conversation; no additional approval needed.

**Goal:** Invalidate only relevant users/projects/tenants and extend stable display caches without serving stale data after known writes.
**Architecture:** Typed cache scopes enter version/data keys (v2 namespace). Messages depend on own user generation plus a global broadcast generation. Project cache uses tenant+project, task statistics use tenant. Public models stay global. Commit callbacks coalesce duplicate scope/region notifications per transaction.
**Tech Stack:** Java17, Spring, existing Lettuce cache, JUnit/H2/Mockito.

## Scope decisions

SCRIPT TTL 60s, MODELS TTL 120s; TASKS/ANALYSIS 2s and MESSAGES5s unchanged. Auth and transaction bypass unchanged. No production deployment in this change.
Message read/delete invalidates actor; targeted send invalidates actual recipients; all-user send invalidates broadcast generation. Project changes and summary persistence invalidate that project. Cover version writes in ScriptWorkflowService, ProjectService and VideoDecompositionService (review_script_version is a separate uncached table), including transaction-template background writes. Execution changes resolve their task tenant/project only when cache enabled; recovery invalidates only successfully recovered tasks. Avoid unrelated global flushes and avoid extending high-churn cache TTLs.

## Tasks

- [x] Add failing scope isolation, targeted/broadcast invalidation, TTL and transaction coalescing tests.
- [x] Add Scope + scoped reads/invalidation, v2 keys and commit notification coalescing in QueryResultCache.
- [x] Update readers, message write hooks and project/execution aspects; cover script version and formal-summary background persistence.
- [x] Test actual Spring AOP/H2 targeted send/read/delete/rollback and isolation, project/background writes, task tenant isolation and model TTL/invalidation.
- [x] Run focused and existing regressions, real Redis tests, independent code review.
- [x] Update operational docs and verify hashes before integrating only this increment into original workspace.

## Validation

Use the existing query cache suites plus new scoped invalidation tests. Assertions must show intended scope reloads while other scope loader counts stay unchanged; user/project auth remains fresh. Record red/green evidence and exact test totals. Production remains disabled. No commit of the unrelated baseline changes.

## Review fixes

Independent review identified sequential Redis timeouts across up to500 recipients and missing invalidation for EpisodeScriptCurrentSelector.selectCurrent. Both were reproduced by new failing tests and fixed: batch stops on first failure with shared2s cooldown, and successful content/fingerprint selection registers project SCRIPT invalidation inside caller transaction. Red/green evidence: outputs/scoped-cache-review-red.log and outputs/scoped-cache-review-green.log. Follow-up review found no remaining blockers.

## Final verification

Cache-focused41 tests passed. Selected clean regression143 tests:141 passed,2 errors,0 skipped. The two ScriptWorkflowControllerTest Agent-code conflicts reproduced identically in the unchanged original source using a clean build. Do not describe the broad suite as all green. Real Redis tests passed and isolated instance/tunnel cleaned up. Independent review has no remaining blockers. See outputs/redis-scoped-cache-test-results.json for exact cases and baseline comparison.
