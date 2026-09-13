## ADDED Requirements

### Requirement: Provide an independently runnable global-understanding Agent
The system SHALL provide an enabled workflow Agent identified by `short-drama-global-understanding` that can independently analyze the current script selected through trusted execution scope.

#### Scenario: Run the Agent for an authorized script
- **WHEN** an authorized user starts the Agent with a trusted tenant, project, and script scope
- **THEN** the system loads the saved Agent definition and compatible model
- **AND** executes the Agent without requiring script content in the caller-provided prompt

#### Scenario: Run without a valid script scope
- **WHEN** the Agent is started without a script or with a script outside the authorized tenant and project
- **THEN** the system rejects the run before invoking the model
- **AND** does not read or modify global-understanding data

### Requirement: Load shared and capability-specific Skills
The Agent SHALL load `short-drama-analysis-foundation` followed by `short-drama-global-understanding-framework` before the first model invocation and SHALL preserve both Skill snapshots in the Agent Run.

#### Scenario: Start with both Skills available
- **WHEN** the Agent starts and both associated Skills are valid
- **THEN** the system composes the system context in configured Skill order
- **AND** records each Skill code, revision, and content snapshot with the run

#### Scenario: Required Skill is unavailable
- **WHEN** either associated Skill cannot be loaded or parsed
- **THEN** the system fails the Agent Run before reading the script
- **AND** does not write a formal result

### Requirement: Read the current script through trusted tooling
The Agent MUST call `read_current_script` before analysis, and the tool SHALL read the active `script.content` identified by server-controlled `scriptId`.

#### Scenario: Read after the script was edited
- **WHEN** the user saves changed script content and starts the Agent again
- **THEN** `read_current_script` returns the newly saved current content
- **AND** records its content hash in server-side run state

#### Scenario: Model supplies a trusted identifier
- **WHEN** a tool call includes `tenantId`, `projectId`, `scriptId`, `userId`, `taskId`, `analysisStageId`, `agentRunId`, or permissions
- **THEN** the runtime rejects the tool call as invalid
- **AND** does not use the model-supplied identifier

### Requirement: Persist one current global-understanding document per script
The system SHALL store one formal global-understanding record for each script, keyed by tenant and `script_id`, using `schema_version` and `content_json` for the extensible analysis body.

#### Scenario: Save the first understanding for a script
- **WHEN** `save_global_understanding` receives a valid payload after a successful current-script read
- **THEN** the tool creates the formal record associated with that script
- **AND** stores the schema version, JSON content, analyzed-content hash, latest Agent Run, actor, and timestamps

#### Scenario: Save a later understanding for the same script
- **WHEN** the Agent is run again for a script that already has a formal record
- **THEN** the tool replaces the current document on the same script-level record
- **AND** does not create a business version-history record
- **AND** does not associate the formal record with `script_version_id`

#### Scenario: Extend the content framework
- **WHEN** a later supported schema version introduces additional analysis attributes
- **THEN** the tool stores them within `content_json`
- **AND** the relational table does not require one column per analysis attribute

### Requirement: Validate the global-understanding content contract
The `save_global_understanding` tool SHALL validate `schemaVersion` and the structured content against the supported JSON Schema before opening the persistence transaction.

#### Scenario: Save valid structured content
- **WHEN** the payload contains all required fields with valid nested relationship and turning-point structures
- **THEN** the tool accepts the payload for persistence

#### Scenario: Save malformed or unsupported content
- **WHEN** required fields are missing, field types are invalid, payload limits are exceeded, or the schema version is unsupported
- **THEN** the tool rejects the call with an actionable validation error
- **AND** leaves the current formal record unchanged

### Requirement: Reject stale analysis results
The save tool SHALL persist a result only when the current script content hash matches the trusted hash recorded by the preceding read in the same Agent Run.

#### Scenario: Script remains unchanged during analysis
- **WHEN** the script hash at save time equals the trusted read hash
- **THEN** the tool may upsert the formal document

#### Scenario: Script changes during analysis
- **WHEN** the script content changes after `read_current_script` and before `save_global_understanding`
- **THEN** the tool fails with `SCRIPT_CONTENT_CHANGED`
- **AND** does not overwrite the existing formal document
- **AND** the Agent Run does not report success

### Requirement: Require ordered tool completion
The Agent Run SHALL succeed only after one successful `read_current_script` call followed by one successful `save_global_understanding` call.

#### Scenario: Model returns text without saving
- **WHEN** the model returns a final response before calling the required save tool
- **THEN** the runtime fails the run with `REQUIRED_TOOL_NOT_CALLED`
- **AND** does not mark an associated analysis stage complete

#### Scenario: Model attempts to save before reading
- **WHEN** the model calls `save_global_understanding` without a successful current-script read in the same run
- **THEN** the runtime rejects the call
- **AND** does not write formal data

#### Scenario: Terminal save succeeds
- **WHEN** `save_global_understanding` commits and returns `saved=true`
- **THEN** the runtime records the tool step and completes the Agent Run immediately
- **AND** does not require an additional model response

### Requirement: Audit independent and pipeline executions
The system SHALL retain Agent configuration, Skill snapshots, model invocation references, tool inputs and outputs, final status, and errors for every global-understanding Agent Run.

#### Scenario: Inspect a successful run
- **WHEN** an authorized administrator opens a completed run
- **THEN** the system exposes the Agent and Skill snapshots and ordered read/save steps
- **AND** excludes provider credentials and server-controlled authorization data

#### Scenario: Run outside a four-stage analysis task
- **WHEN** the Agent is executed independently with no analysis stage
- **THEN** the system persists the formal current document and Agent Run audit
- **AND** does not create a synthetic four-stage analysis task
