# Verification Notes

## Focused tests

- `ScopedAssetReextractionServiceTest`: 14 tests passed.
- `AssetExtractionCoordinationTest`: 13 tests passed after updating the expected parent aggregation error.
- The final clean combined scoped re-extraction, service-level concurrency, coordination, Agent adapter, and execution-handler run covered 37 tests with zero failures and zero errors.
- Independent code review confirmed immediate peer interruption on claim loss and real `executeUnit` transaction overlap at concurrency 2; no release blockers remained.

## Full backend suite

`mvn -q -f backend/pom.xml test` compiled the full backend and ran the complete suite. Four failures were investigated:

- `RemainingAnalysisAgentsEndToEndTest.failuresRollbackAndRetryOnlyTheFailedEpisodeWithoutPrematureRetirement`: existing ambiguous asset fixture (`c_1`, `c_2`).
- `ScriptWorkflowControllerTest.scopedAssetReextractionDoesNotReuseARequestAfterEpisodeSourceChanges`: existing admission assertion receives no `REUSED` marker.
- `RemainingAnalysisAgentBootstrapTest.createsThreeStableDefinitionsAndKeepsStartupIdempotent`: existing max-steps expectation is 6 while the definition is 12.
- `ScriptWorkflowControllerTest.createsAndReanalyzesInitialScriptTasks`: transient full-suite timing failure; the method passes when rerun alone on the changed tree.

The first three failures reproduce on an isolated, unmodified `8c120b1` worktree. They are unrelated to scoped re-extraction concurrency. The fourth passes in focused reruns and does not exercise `ScopedAssetReextractionService`.

The change contract intentionally guarantees the configured per-operation fan-out bound. Child-call-level cross-instance model quota enforcement is a separate platform concern because the existing quota counts parent executions rather than individual Agent calls.
