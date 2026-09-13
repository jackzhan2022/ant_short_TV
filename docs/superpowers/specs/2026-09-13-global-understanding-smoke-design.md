# Global Understanding Smoke Verification

## Scope

Complete task 8.3 of `add-global-understanding-agent` with a non-production end-to-end verification. This work adds verification evidence only; it does not change the script-analysis runtime, API contracts, database schema, or UI behavior.

## Approach

Use a non-production tenant, project, and script with the workflow Agent, both required Skills, and a compatible model enabled. Exercise the existing user workflow and record the resulting analysis stage, formal global-understanding row, Agent Run, and workspace progress.

The smoke sequence is:

1. Start first analysis and verify the global-understanding Agent reads the current script, saves a formal document, and updates progress to completed.
2. Edit the script and explicitly reanalyze it. Verify that the same formal row is overwritten with the new source hash rather than creating a second current row.
3. Change the script during a running analysis. Verify that the stale save is rejected and does not overwrite the prior formal document.
4. Retry a failed analysis and verify that it completes through the normal execution and accounting paths.
5. Reload the production workbench and verify that durable stage and Agent Run progress are restored.

## Evidence And Completion

Record the non-production environment, anonymized project and script identifiers, execution timestamps, formal-row identity and hashes, Agent Run identifiers, visible progress states, retry outcome, and stale-content rejection. Redact credentials, script body, tenant identifiers, and provider secrets. Mark task 8.3 complete only after each assertion has evidence.

## Failure Handling

If configuration, provider access, billing, or the non-production runtime prevents a case from executing, leave task 8.3 unchecked and record the actionable failure. Do not use a mocked result as a substitute for this smoke test.
