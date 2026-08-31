## MODIFIED Requirements

### Requirement: Execute the four analysis stages in order
The system SHALL execute the stages in this order: global story understanding, intelligent episode splitting, episode summary extraction, and character/scene recognition. The global story understanding stage SHALL invoke the enabled `short-drama-global-understanding` workflow Agent, while the remaining stages retain their existing executors until separately migrated.

#### Scenario: Complete global understanding through the Agent
- **WHEN** the global story understanding stage is ready to run
- **THEN** the system invokes the saved workflow Agent with trusted tenant, project, script, task, stage, and user scope
- **AND** the Agent reads the current script through `read_current_script`
- **AND** the stage succeeds only after `save_global_understanding` commits the formal script-level document
- **AND** the persisted normalized content becomes the global-understanding input for intelligent episode splitting

#### Scenario: Advance after successful stage
- **WHEN** a stage produces and persists a valid result according to its stage-specific completion contract
- **THEN** the system marks that stage succeeded
- **AND** starts the next stage with the previous result as input

#### Scenario: Preserve failed stage
- **WHEN** a stage fails
- **THEN** the system marks the stage failed with an actionable error
- **AND** does not mark later stages successful
- **AND** preserves all earlier successful results

#### Scenario: Run a non-migrated later stage
- **WHEN** episode splitting, episode summary extraction, or character/scene recognition becomes ready
- **THEN** the system executes that stage through its existing implementation
- **AND** preserves the established ordering, retry, billing, and result contracts

## ADDED Requirements

### Requirement: Keep formal global understanding separate from version-bound analysis evidence
The system SHALL maintain the current formal global-understanding document by `script_id` while retaining existing task, script-version, call, and stage-result evidence for analysis audit and downstream compatibility.

#### Scenario: Persist from a version-bound pipeline task
- **WHEN** a version-bound analysis task completes its global-understanding Agent stage
- **THEN** the formal document is upserted by current `script_id` without `script_version_id`
- **AND** the task and diagnostic result retain their existing version-bound metadata

#### Scenario: Retrieve downstream global context
- **WHEN** intelligent episode splitting starts after the Agent stage succeeds
- **THEN** it receives the normalized content committed by the global-understanding save tool
- **AND** does not depend on parsing the Agent's final natural-language response
