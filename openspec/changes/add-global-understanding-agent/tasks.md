## 1. Persistence Contract

- [x] 1.1 Add migration tests that assert the script-level global-understanding table, JSON/audit columns, foreign keys, and unique `(tenant_id, script_id)` constraint.
- [x] 1.2 Add a Flyway migration for `script_global_understanding` without `script_version_id` or business-history tables.
- [x] 1.3 Add the formal global-understanding entity/repository and tests for first insert, same-script replacement, tenant isolation, and current-document retrieval.

## 2. Trusted Script Scope and Agent Runtime Contract

- [x] 2.1 Add failing scope-guard tests for missing, cross-project, deleted, and model-supplied script/stage/run identifiers.
- [x] 2.2 Extend formal workflow-Agent input, run audit, and tool execution context with server-controlled `scriptId`, `analysisStageId`, and `agentRunId` where required.
- [x] 2.3 Add failing runner tests for save-before-read, final-text-without-save, duplicate terminal save, wrong tool order, and successful terminal completion.
- [x] 2.4 Implement the business-supplied required-tool sequence and terminal-tool policy without changing default behavior for unrelated workflow Agents.
- [x] 2.5 Add run-scoped tool state that records the trusted script content hash produced by the successful read tool.

## 3. Current Script Read Tool

- [x] 3.1 Add tool-schema and authorization tests for `read_current_script`, including rereading content after an ordinary script edit.
- [x] 3.2 Implement and register `read_current_script` with empty model arguments, trusted script lookup, SHA-256 hashing, bounded output, and run-state recording.
- [x] 3.3 Extend the tool catalog and Agent management test-run scope to require an explicitly selected authorized script for this tool.

## 4. Global Understanding Save Tool

- [x] 4.1 Add JSON Schema tests for supported `schemaVersion`, required global fields, nested relationships, nested turning points, type constraints, array limits, and payload-size limits.
- [x] 4.2 Add transactional service tests for create, same-script overwrite, cross-tenant rejection, missing prior read, stale hash rejection, and rollback on persistence failure.
- [x] 4.3 Implement and register `save_global_understanding` using only model-supplied `schemaVersion` and `content`, with all business identity taken from trusted run context.
- [x] 4.4 Persist the formal document, analyzed hash, latest Agent Run, actor, and timestamps atomically; when an analysis stage exists, persist normalized evidence and stage success in the same transaction.
- [x] 4.5 Return and validate the terminal result contract containing `saved`, formal record ID, script ID, saved hash, and optional stage status.

## 5. Skills and Agent Definition

- [x] 5.1 Create `short-drama-analysis-foundation/SKILL.md` with current-source, non-fabrication, naming consistency, authorization, tool-failure, stale-content, language, and completion rules.
- [x] 5.2 Create `short-drama-global-understanding-framework/SKILL.md` with responsibility boundaries, field semantics, structured document shape, and pre-save quality checks.
- [x] 5.3 Add validation tests for both Skill files and their required load order and run snapshots.
- [x] 5.4 Add idempotent bootstrap for the enabled `short-drama-global-understanding` Agent with a compatible text tool-calling model, maxSteps `4`, both Skills, and exactly the read/save tools.
- [x] 5.5 Add Agent bootstrap and prompt-contract tests proving that repeated startup does not duplicate or silently overwrite administrator-modified definitions.

## 6. Script Analysis Pipeline Integration

- [x] 6.1 Add adapter tests proving the global stage invokes the saved workflow Agent with trusted script/task/stage scope and passes committed normalized content to episode splitting.
- [x] 6.2 Implement the global-understanding Agent adapter behind a server configuration flag while keeping the three later stages and the legacy fallback unchanged.
- [x] 6.3 Preserve unified model invocation, point reservation/settlement, call logs, retry semantics, and stale-task behavior when the stage uses the workflow Agent.
- [x] 6.4 Add crash-reconciliation behavior that recognizes a committed formal row and stage success linked to an incomplete Agent Run without repeating model inference.

## 7. Progress and Workspace Data

- [x] 7.1 Add progress mapping tests for waiting, reading, analyzing, saving, committed, stale-content failure, validation failure, and retry states.
- [x] 7.2 Map persisted Agent/model/tool steps to the global-understanding analysis stage and expose the current action and Agent Run reference in the script workspace response.
- [x] 7.3 Expose the current formal global-understanding document from the script workspace/domain API without depending on Agent final text or parsing diagnostic raw output.
- [x] 7.4 Update the existing script analysis state UI to restore and display durable global-Agent progress while retaining the current four-stage layout.

## 8. Verification and Rollout

- [x] 8.1 Run focused backend unit/integration tests for workflow Agent runtime, tools, persistence, analysis execution, authorization, billing, and workspace response.
- [x] 8.2 Run frontend type checking, targeted tests, Biome checks, and Ant Design lint for affected script-workbench code.
- [ ] 8.3 Run a non-production end-to-end smoke test covering first analysis, script edit followed by independent reanalysis, same-row overwrite, mid-run script change rejection, retry, and page progress restoration.
- [x] 8.4 Enable the adapter in the target environment only after verifying the Agent, both Skills, compatible model, tool associations, formal table, and rollback flag health checks.
