## ADDED Requirements

### Requirement: Read-only backend tool registry
The backend SHALL provide a code-defined, read-only tool registry. Each tool definition MUST expose a stable code, name, description, typed input schema, typed output schema, risk level, and executor. Management APIs SHALL allow authorized Agent editors to discover registered tools but MUST NOT allow tool implementation or schema mutation.

#### Scenario: Agent editor loads tools
- **WHEN** an authorized Agent editor requests the tool catalog
- **THEN** the system SHALL return metadata for currently registered tools without exposing executor implementation or secrets

#### Scenario: Agent references an unknown tool
- **WHEN** an Agent save or run references a tool code absent from the registry
- **THEN** the system SHALL reject the operation before provider contact or tool execution

### Requirement: Model-native tool-calling loop
The Agent runtime SHALL construct each formal run from the current saved Agent configuration, current ordered Skill contents, and explicitly associated registered tools. It SHALL invoke the selected platform model through the shared AI routing and invocation services using model-native tool calling, execute validated tool calls, return tool results to the model, and stop on a final model response, a configured maximum step count, or a terminal error.

#### Scenario: Agent completes a multi-step workflow
- **WHEN** the model requests one or more allowed valid tool calls and then returns final content within the configured step limit
- **THEN** the runtime SHALL execute each call in order, feed normalized results back to the model, and return the final content

#### Scenario: Step limit is reached
- **WHEN** the model has not produced a final response before the Agent's maximum step count
- **THEN** the runtime SHALL stop further provider and tool calls and mark the run failed with a normalized step-limit error

#### Scenario: Provider invocation fails
- **WHEN** a model round fails through the shared invocation service
- **THEN** the runtime SHALL preserve the normalized AI error and linked call log in the Agent run record

### Requirement: Tool allowlist and trusted scope enforcement
The runtime MUST expose and execute only tools explicitly associated with the Agent. It MUST validate every model-supplied argument against the registered input schema and independently inject trusted tenant, user, project, episode, task, and permission context from the authenticated run request. Model-supplied text MUST NOT override trusted scope or grant access to another resource.

#### Scenario: Model requests an unassociated tool
- **WHEN** the model emits a call for a registered tool that is not associated with the running Agent
- **THEN** the runtime SHALL refuse execution, record the violation, and terminate the run with a normalized authorization error

#### Scenario: Model supplies another project identifier
- **WHEN** tool arguments contain a project or episode identifier outside the authenticated execution scope
- **THEN** the runtime SHALL ignore or reject the untrusted scope value and MUST NOT access the out-of-scope resource

#### Scenario: Tool arguments fail schema validation
- **WHEN** the model emits arguments that do not satisfy the registered input schema
- **THEN** the runtime SHALL not invoke the executor and SHALL record a normalized validation failure

### Requirement: Initial screenplay tool set
The registry SHALL provide the implemented screenplay tools required for Agent-authored workflows, including project context reading, episode listing and reading, adjacent-episode reading, analysis and asset reading, screenplay format validation, and episode script saving. `save_episode_script` MUST create a new episode script version and atomically select it as the episode's current version while retaining prior versions.

#### Scenario: Agent reads episode context
- **WHEN** an allowed read tool is called within an authorized project and episode scope
- **THEN** the tool SHALL return only the requested in-scope current data using its declared output schema

#### Scenario: Agent saves an episode screenplay
- **WHEN** an allowed `save_episode_script` call contains valid screenplay content for the authorized episode
- **THEN** the system SHALL create a new version, mark that version current, retain previous versions, and return the new version metadata

#### Scenario: Screenplay save cannot become current
- **WHEN** version creation or current-version selection fails
- **THEN** the system SHALL roll back the save transaction and leave the previously current version unchanged

### Requirement: Reproducible Agent run audit
Every formal and test run SHALL record its type, actor, scope, status, timing, selected model and limits, Agent prompt snapshot, ordered Skill codes with content hashes and content snapshots, associated tool codes, model call references, tool arguments and normalized results, step sequence, final output, and normalized error. Sensitive values MUST be redacted according to the existing logging policy.

#### Scenario: Successful run is inspected
- **WHEN** an authorized viewer opens a completed Agent run
- **THEN** the system SHALL display the configuration snapshot and ordered step history sufficient to determine which prompt, Skills, tools, and model calls produced the result

#### Scenario: Tool execution fails
- **WHEN** an allowed tool executor returns a terminal failure
- **THEN** the runtime SHALL record the failed step and normalized error, stop or continue according to the tool's declared failure policy, and finalize the run consistently
