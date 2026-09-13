## ADDED Requirements

### Requirement: Independent workflow Agent lifecycle
The system SHALL provide an Agent（新）configuration domain and API that are independent from the existing built-in Agent domain. An authorized editor SHALL be able to create, view, edit, copy, enable, disable, and delete a workflow Agent. Each Agent MUST have an immutable unique code, name, description, system prompt, model, generation limits, execution step limit, and status. Saving a valid edit SHALL replace the current configuration atomically and SHALL take effect for subsequent runs without a publish step or configuration version.

#### Scenario: Editor creates an Agent
- **WHEN** an authorized editor submits a unique valid code and all required Agent fields
- **THEN** the system SHALL create the Agent and make its saved configuration available to subsequent runs

#### Scenario: Editor updates an Agent
- **WHEN** an authorized editor saves valid changes to an existing Agent
- **THEN** the system SHALL atomically update the current configuration without changing its code and the next formal run SHALL use the updated values

#### Scenario: Invalid configuration is submitted
- **WHEN** an editor submits a missing, disabled, or incompatible model, invalid generation limits, or an empty required field
- **THEN** the system SHALL reject the request without partially updating the Agent or its associations

#### Scenario: Disabled Agent is invoked
- **WHEN** a caller attempts a formal run of a disabled Agent
- **THEN** the system SHALL reject the run before any model or tool invocation

### Requirement: Explicit Skill and tool associations
Each workflow Agent SHALL explicitly associate zero or more current Skills in a deterministic load order and zero or more registered tools. The Agent editor SHALL display the available Skills and tools, persist selections as associations, and allow a selected tool's readable invocation text containing its stable tool code to be inserted at the prompt cursor. Prompt text alone MUST NOT create a Skill or tool authorization.

#### Scenario: Editor saves ordered Skills and tools
- **WHEN** an editor selects Skills in an explicit order and selects registered tools before saving
- **THEN** the system SHALL atomically persist the ordered Skill associations and tool allowlist with the Agent configuration

#### Scenario: Editor inserts a tool into the prompt
- **WHEN** an editor chooses a tool insertion action at the current prompt cursor
- **THEN** the editor SHALL add the tool to the Agent allowlist and insert readable text containing the stable tool code without using a special placeholder syntax

#### Scenario: Prompt names an unselected tool
- **WHEN** prompt text contains the code of a tool that is not associated with the Agent
- **THEN** the saved prompt SHALL NOT authorize or expose that tool at runtime

### Requirement: Safe Agent deletion and copying
The system SHALL prevent deletion of a workflow Agent while an active business binding references it. Copying an Agent SHALL create a new immutable code and duplicate the editable configuration and associations without copying business bindings or run history.

#### Scenario: Referenced Agent deletion is attempted
- **WHEN** an authorized editor attempts to delete an Agent that is referenced by a business configuration
- **THEN** the system SHALL reject deletion and identify that references must be removed first

#### Scenario: Agent is copied
- **WHEN** an authorized editor copies an Agent using a new valid unique code
- **THEN** the system SHALL create an independent Agent with equivalent editable configuration and associations but no business references or run records

### Requirement: Draft-safe Agent test run
The Agent editor SHALL allow an authorized editor to test either the saved Agent or the current unsaved form configuration. A test run MUST validate models, Skills, tools, scope, and limits using the same runtime controls as a formal run, and MUST NOT persist the unsaved form as the current Agent configuration.

#### Scenario: Editor tests unsaved changes
- **WHEN** an editor starts a test with valid unsaved prompt or association changes
- **THEN** the system SHALL execute those temporary values, label the run as a test, and leave the saved Agent configuration unchanged

#### Scenario: Temporary test configuration is invalid
- **WHEN** a test request contains an unavailable Skill, tool, model, or invalid limit
- **THEN** the system SHALL reject the test before provider contact and preserve the saved Agent configuration

### Requirement: Workflow Agent permissions and audit metadata
The system SHALL enforce independent view and edit permissions for Agent（新）. View permission SHALL permit listing, detail viewing, association viewing, and run-log viewing; edit permission SHALL be required for mutation and test execution. Every mutation MUST record the acting user and creation or update timestamps.

#### Scenario: View-only user opens Agent（新）
- **WHEN** a user has the Agent（新）view permission but not its edit permission
- **THEN** the system SHALL show Agent details and run logs without exposing enabled mutation or test actions

#### Scenario: Unauthorized mutation is requested
- **WHEN** a caller without the Agent（新）edit permission invokes a mutation or test endpoint
- **THEN** the system SHALL deny the operation without changing configuration or starting a run
