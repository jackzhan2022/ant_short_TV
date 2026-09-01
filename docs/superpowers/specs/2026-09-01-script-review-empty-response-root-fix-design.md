# Script Review Empty-Response Root Fix Design

## Context

Production workflow-agent run 52 used `deepseek-v4-flash`. Its third model call consumed 17,131 prompt tokens and the full 16,384 completion-token budget, returned `finish_reason=length` with `truncated=true`, and produced neither visible content nor tool calls. The runner then reported `REQUIRED_TOOL_NOT_CALLED`, which hid the actual model-output failure.

## Design

1. `WorkflowAgentRunner` will send `thinking.type=disabled` for the `script-review` agent, matching the existing episode-splitting tool-workflow behavior. Other agents keep their current thinking configuration.
2. If a script-review model call is truncated or finishes with `length` before emitting a tool call, the runner will append a corrective user message and continue within the existing shared step budget. The message will require the next trusted read or terminal save instead of ordinary text.
3. Tool authorization, contract ordering, terminal-tool behavior, timeouts, and maximum steps remain unchanged. Repeated truncation still terminates through the existing bounded step limit.

## Testing

- Add a runner regression test that asserts every script-review provider request disables thinking.
- Reproduce a truncated empty response after successful context/content reads, then verify the model can continue with history read and terminal save.
- Re-run `WorkflowAgentRunnerTest` and `ScriptReviewAgentRunContractTest` before packaging and deployment.

## Deployment Verification

Deploy a versioned release, confirm service/API health, retry production review task 9, and verify the new workflow run advances beyond the previous truncated model step and saves unit 1 successfully.
