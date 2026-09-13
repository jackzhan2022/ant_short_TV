## ADDED Requirements

### Requirement: Report durable global-understanding Agent progress
The system SHALL derive global-understanding stage progress from durable Agent and tool events and SHALL display completion only after the formal script-level document is committed.

#### Scenario: Agent is waiting to run
- **WHEN** the global-understanding stage is pending
- **THEN** the UI reports that it is waiting for the剧情全局理解 Agent
- **AND** does not display the stage as completed

#### Scenario: Agent reads the current script
- **WHEN** the `read_current_script` tool is running or has just succeeded
- **THEN** the UI reports that the current script is being read
- **AND** persists a monotonic stage-progress update

#### Scenario: Agent analyzes the script
- **WHEN** the read step succeeded and the model is producing the structured understanding
- **THEN** the UI reports that the Agent is understanding the global story
- **AND** does not claim provider-internal token progress

#### Scenario: Agent saves the formal result
- **WHEN** `save_global_understanding` is executing
- **THEN** the UI reports that the global understanding is being saved
- **AND** keeps progress below 100 percent until the transaction commits

#### Scenario: Formal persistence succeeds
- **WHEN** the terminal save tool commits and returns `saved=true`
- **THEN** the stage displays 100 percent and completed
- **AND** the completed state survives leaving and reopening the script page

#### Scenario: Agent or tool fails
- **WHEN** model invocation, Skill loading, authorization, schema validation, stale-content validation, or persistence fails
- **THEN** the UI reports the durable failure reason
- **AND** exposes the existing authorized retry action
