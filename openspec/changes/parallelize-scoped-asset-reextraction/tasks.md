## 1. Concurrency Contract Tests

- [x] 1.1 Extend `ScopedAssetReextractionServiceTest` with blocking child executions that prove multiple episode units overlap and never exceed the configured fan-out limit.
- [x] 1.2 Add regression cases proving one child failure does not prevent peer success persistence and incomplete coverage prevents scoped finalization.
- [x] 1.3 Add cancellation and execution-claim-loss cases proving new useful work stops, stale completion cannot finalize, and committed units remain recoverable.

## 2. Bounded Scoped Fan-Out

- [x] 2.1 Inject and clamp `ai.workflow-agent.fanout-concurrency` for scoped re-extraction using the same 1-16 bound as normal episode fan-out.
- [x] 2.2 Replace the serial runnable-unit loop with a fixed-size executor that performs per-unit ownership/source validation, claim, Agent execution, and outcome persistence outside any long database transaction.
- [x] 2.3 Aggregate model-call evidence thread-safely, isolate normal child failures, propagate execution-claim loss, and shut down worker threads on every exit path.
- [x] 2.4 Recompute persisted progress after workers settle and retain the existing complete-only formal-evidence check and scoped finalizer.

## 3. Recovery And Compatibility Verification

- [x] 3.1 Verify retries reuse successful commit evidence and schedule only pending, failed, or interrupted scoped units.
- [x] 3.2 Run the focused scoped re-extraction, extraction coordination, Agent adapter, and execution-handler test suites.
- [x] 3.3 Run backend compilation and the broader backend test suite, documenting any unrelated pre-existing failures.

## 4. Production Rollout

- [ ] 4.1 Build an executable backend JAR from the verified commit and confirm its artifact structure and SHA-256 checksum.
- [ ] 4.2 Deploy through a new versioned release, atomically switch `/opt/antv/current`, restart `antv.service`, and verify service/API health with the previous release retained for rollback.
- [ ] 4.3 Cancel the active serial scoped re-extraction through the supported execution workflow and submit a new `ALL + REGENERATE_ALL` operation.
- [ ] 4.4 Verify the replacement operation uses the current Skill revision, runs up to four episode children concurrently, advances persisted progress, and produces no duplicate active assets or bindings.
