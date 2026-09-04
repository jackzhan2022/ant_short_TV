## 1. Execution Lease Heartbeat

- [x] 1.1 Add focused worker tests proving a running handler renews its claim, closes its heartbeat schedule on exit, and does not publish a terminal state after heartbeat ownership loss.
- [x] 1.2 Implement a shared scheduled execution-lease guard that renews the token-qualified claim and exposes ownership loss to the worker.
- [x] 1.3 Integrate the lease guard into `AiExecutionWorker` with unconditional cleanup and claim-loss handling around both success and failure completion paths.
- [x] 1.4 Add heartbeat interval configuration with startup validation that it is positive and shorter than the execution claim timeout.

## 2. Attempt-Safe Script Analysis Recovery

- [x] 2.1 Add a regression test in which an expired script-analysis attempt returns after a replacement attempt starts and verify the stale attempt cannot mark the task or stage failed.
- [x] 2.2 Add active execution/attempt fencing before script-analysis terminal writes and propagate `AiExecutionClaimLostException` without converting it into a business failure.
- [x] 2.3 Add fan-out recovery tests proving compatible snapshots retain `SUCCEEDED` units, convert interrupted `RUNNING` units to `STALE`, and schedule only `PENDING`, `FAILED`, or `STALE` units.
- [x] 2.4 Recompute and persist parent stage and snapshot progress when a replacement attempt opens so the domain state reflects active recovery rather than a stale terminal failure.

## 3. Bounded Asset Payload Correction

- [x] 3.1 Add workflow-runner tests for a missing `save_episode_assets` evidence field that succeeds after one corrected tool call.
- [x] 3.2 Add a workflow-runner test proving two consecutive invalid asset-save payloads terminate without a third correction or tool invocation.
- [x] 3.3 Implement one run-local correction opportunity for correctable `save_episode_assets` argument/schema failures, returning the precise tool error to the model without synthesizing evidence.

## 4. Verification and Production Recovery

- [x] 4.1 Run the focused execution, script fan-out, and workflow-agent test classes and resolve all regressions.
- [x] 4.2 Run the complete backend test suite and confirm the change introduces no schema, billing, or API contract regression.
- [x] 4.3 Configure production with a one-minute heartbeat interval, a temporary sixty-minute claim timeout, and fan-out concurrency four; back up the prior environment.
- [ ] 4.4 Deploy and restart the backend, verify service and HTTP health, and observe heartbeat timestamps advance for the active execution.
- [ ] 4.5 Retry the failed project 27 analysis stage and verify only unsuccessful snapshot units run while previously successful units remain unchanged.
- [ ] 4.6 Confirm all 58 units and the fan-out snapshot, analysis stage, domain task, and unified execution reach successful terminal states, then record the final attempt and error summary.
