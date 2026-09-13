## Context

The repository already has two separate foundations that this change must connect. The legacy script-analysis pipeline owns four ordered stages and persists diagnostic stage results, while the newer workflow-agent module owns editable Agent definitions, file-backed Skills, model-native tool calling, tool allowlists, trusted project/episode scope, and run-step audit records. The global-understanding stage still bypasses the workflow-agent runtime, and the current screenplay tool catalog cannot read a script-level source or persist a formal global-understanding document.

The requested Agent must be independently runnable against the script currently selected by the caller. It must not receive script content as an untrusted prompt argument, must not depend on a script-version business record, and must overwrite one current formal result associated with `script_id`. Existing analysis tasks and `script_analysis_result` may continue to retain version-bound execution evidence; that audit lifecycle is distinct from the new formal business document.

## Goals / Non-Goals

**Goals:**

- Provide an enabled, independently runnable `short-drama-global-understanding` workflow Agent.
- Make the Agent load one shared analysis-foundation Skill and one global-understanding-framework Skill.
- Require the Agent to read the current script through a trusted tool and persist through a dedicated terminal write tool.
- Store one extensible current JSON document per script without a business version history.
- Prevent a result derived from stale script content from overwriting the current document.
- Integrate the Agent into the first stage of the existing four-stage analysis pipeline while preserving the other three stages.
- Make tool execution and persistence observable through existing Agent Run and analysis progress records.

**Non-Goals:**

- Migrating episode splitting, episode summaries, or character/scene/prop recognition to workflow Agents.
- Designing the final presentation of global-understanding content on the script page.
- Creating business history, rollback, comparison, or merge behavior for global-understanding documents.
- Defining how later user edits interact with future reanalysis beyond replacing the current document.
- Allowing administrators to author executable tool implementations or tool schemas from the UI.

## Decisions

### 1. Use a dedicated Agent with two automatically loaded Skills

Create or idempotently seed the enabled Agent code `short-drama-global-understanding`. Associate Skills in this order:

1. `short-drama-analysis-foundation` defines source-of-truth, non-fabrication, naming consistency, tool discipline, stale-content handling, language, and completion rules shared by all future script-analysis Agents.
2. `short-drama-global-understanding-framework` defines the global-analysis responsibility, exclusions, semantic field definitions, structured content contract, and pre-save quality checks.

The Agent system prompt remains short and operational: call `read_current_script` first, analyze with the loaded Skills, call `save_global_understanding` once, and stop after success. Keeping analysis semantics in Skills allows the content framework to evolve without duplicating it in the Agent prompt. Exact machine validation remains in the write tool's JSON Schema so prose instructions are never the enforcement boundary.

An Agent-specific monolithic system prompt was considered but rejected because it would duplicate the shared rules needed by the next three Agents. Dynamically asking the model to invoke a Skill was also rejected because this runtime loads Skill snapshots before inference; Skills are context, not executable tools.

### 2. Extend trusted execution scope with script and optional analysis-stage identity

Extend formal workflow-Agent execution with server-provided `scriptId` and optional `analysisStageId`. The run record and `ToolExecutionContext` receive these values after authorization. Model tool arguments MUST NOT contain `tenantId`, `userId`, `projectId`, `scriptId`, `taskId`, `analysisStageId`, `agentRunId`, or permissions.

The scope guard verifies that the script is active and belongs to the trusted tenant/project and that the user has `SCRIPT:VIEW` before reading or `SCRIPT:EDIT` before saving. When an analysis stage is supplied, it must belong to the trusted task, script, tenant, and project and must represent `GLOBAL_UNDERSTANDING`.

Inferring the script from the latest project row was rejected because a project can evolve to contain multiple script records and “latest” is ambiguous. Passing `scriptId` in model-visible input was rejected because business identity must not be model-controlled.

### 3. Add a run-scoped required tool contract

The script-analysis adapter invokes the workflow runner with an internal execution contract:

```text
required sequence: read_current_script -> save_global_understanding
terminal tool: save_global_understanding
```

The runner tracks successful calls in run-scoped state. It rejects saving before a successful read, rejects duplicate terminal saves, and fails with `REQUIRED_TOOL_NOT_CALLED` if the model returns final text before the required write. A successful terminal tool ends the run immediately; no extra model round is required merely to restate success. With the current accounting of model and tool steps, the Agent default `maxSteps` is four: model/read, tool/read, model/save, tool/save.

Deriving success from final model text was rejected because the model can claim completion without persistence. Making every workflow Agent globally require one fixed sequence was also rejected; the contract is attached by the trusted business adapter so other workflow Agents retain their existing behavior.

### 4. Read the current script through a dedicated tool and retain its hash in run state

`read_current_script` accepts no model-supplied business identifiers. It uses the trusted `scriptId`, reads the active `script.content`, and returns the content, its SHA-256 hash, and `updatedAt`. The executor also records the hash in server-side run state; the model does not establish the trusted expected hash by copying it into the save call.

Reading through a tool makes the Agent independently runnable after any script edit and from either the script workflow or an authorized Agent test surface. Injecting the full script into the initial user message was considered but rejected because it makes the Agent dependent on each caller assembling the correct current source.

### 5. Persist one current extensible document per script

Add `script_global_understanding` with fixed ownership and audit metadata plus a JSON body:

```text
id
tenant_id
project_id
script_id
schema_version
content_json
analyzed_content_hash
last_agent_run_id
created_by
updated_by
created_at
updated_at
```

Enforce a unique key on `(tenant_id, script_id)` and foreign keys to the owning project/script and latest Agent Run where compatible with existing migration conventions. Do not add `script_version_id`. Reanalysis performs an upsert that replaces `schema_version`, `content_json`, `analyzed_content_hash`, `last_agent_run_id`, `updated_by`, and `updated_at` on the same row.

The JSON document contains the framework fields such as `logline`, `synopsis`, `genres`, `themes`, `worldSetting`, `coreConflict`, `relationships`, `turningPoints`, `ending`, `endingHook`, `narrativeStyle`, and `targetAudience`. `schema_version` identifies the tool contract used to validate the document. Adding future analysis attributes changes the Skill and tool schema rather than the relational table.

A wide table was rejected because every framework extension would require a migration. EAV storage was rejected because the document is read and replaced as a unit and does not require independent relational querying of arbitrary attributes.

### 6. Make the save tool authoritative and stale-safe

`save_global_understanding` accepts only `schemaVersion` and the structured `content` document. Its JSON Schema enforces required fields, nested relationship and turning-point shapes, type constraints, array limits, and payload-size limits.

Within one database transaction, the tool:

1. verifies the trusted scope and required prior read;
2. locks the active script row and hashes its current content;
3. rejects with `SCRIPT_CONTENT_CHANGED` if the hash differs from run state;
4. upserts the formal document by `(tenant_id, script_id)`;
5. records the normalized payload and Agent Run linkage in existing analysis evidence when an analysis stage is present;
6. marks the supplied global-understanding stage succeeded only after the formal write succeeds; and
7. returns `saved`, the formal record ID, `scriptId`, saved hash, and stage status when applicable.

If the Agent runs independently without an analysis stage, the same transaction writes the formal document and the workflow run completes, but no synthetic four-stage task is created. Tool-call arguments and results remain available in `ai_workflow_agent_run_step` for audit.

### 7. Adapt only the first legacy analysis stage

Add a global-understanding stage adapter that invokes the saved and enabled Agent with trusted tenant, project, script, task, stage, and user scope. The existing scheduler, point reservation, retry entry point, and downstream stage ordering remain in place. Once the terminal write tool succeeds, the adapter exposes the normalized formal JSON to the existing downstream context expected by episode splitting.

The other three stages continue through `ScriptAnalysisExecutionService`. This incremental boundary avoids combining four Agent migrations and their different relational outputs into one change.

### 8. Report progress from durable steps

Map durable events to the existing stage progress contract:

```text
PENDING       0   等待剧情全局理解 Agent
RUNNING      10   正在读取当前剧本
RUNNING      35   正在理解剧情全局
RUNNING      80   正在保存全局理解
SUCCEEDED   100   全局理解已保存
FAILED        -   actionable error from model, tool, authorization, schema, or stale content
```

Provider-internal token progress is not inferred. The page restores status from persisted Agent Run steps and analysis-stage state. `100` percent is emitted only after the formal row commits.

## Risks / Trade-offs

- [A Skill edit can change production behavior immediately] → Preserve Skill revision/content snapshots in every run, validate the write payload independently, and keep the Agent disable switch available.
- [The script can change while the model is analyzing] → Store the read hash in trusted run state, lock and re-hash before upsert, and fail without modifying the current document when hashes differ.
- [A process can stop after the formal write but before outer run bookkeeping completes] → Perform formal persistence and optional stage success in the terminal tool transaction; reconcile an incomplete Agent Run from the row's `last_agent_run_id` rather than repeating model inference.
- [JSON is flexible but harder to query relationally] → Treat the document as a read-as-a-unit aggregate; add deliberate generated/indexed projections only when a real query requirement appears.
- [The first migrated stage and three legacy stages use different executors] → Keep a narrow adapter that returns the same normalized global-understanding contract consumed by episode splitting and cover it with compatibility tests.
- [Independent test runs can overwrite formal production data] → Require explicit project/script selection, normal edit permission, and a visible write-risk confirmation in the management test surface; do not provide a fake non-persisting mode for this formal Agent.

## Migration Plan

1. Add the formal table and indexes without changing the legacy stage path.
2. Add trusted script scope, run-scoped tool state, both tools, validation, and automated tests.
3. Add the two Skill files and idempotently seed the disabled Agent; validate its model compatibility and tool associations.
4. Run authorized smoke tests on a non-production script, including stale-content and repeat-run overwrite cases.
5. Enable the Agent and switch only `GLOBAL_UNDERSTANDING` to the adapter behind a server configuration flag.
6. Verify formal rows, run-step audit, billing, retries, progress restoration, and downstream episode splitting before making the adapter the default.

Rollback disables the adapter flag and Agent so the legacy global-understanding call path resumes. The new formal table, Skills, Agent configuration, and run records remain intact to avoid data loss; no destructive database rollback is required.

## Open Questions

None for this change. User-edit protection and business-history behavior are explicitly deferred.
