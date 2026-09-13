# script-analysis-pipeline Specification

## Purpose
TBD - created by archiving change add-script-analysis-pipeline. Update Purpose after archive.
## Requirements
### Requirement: Start analysis only for first project creation with script content
The system SHALL create an analysis task only when a project is created for the first time with non-empty initial script content.

#### Scenario: Create a project with initial script content
- **WHEN** an authorized user creates a new project with valid non-empty script content
- **THEN** the system persists the project and initial script version
- **AND** creates one analysis task bound to that script version
- **AND** schedules the first analysis stage

#### Scenario: Create a project without initial script content
- **WHEN** an authorized user creates a new project without script content
- **THEN** the system persists the project without creating an analysis task

#### Scenario: Initial project creation fails
- **WHEN** the project or initial script version cannot be persisted
- **THEN** the system SHALL NOT create an analysis task
- **AND** returns the creation error to the user

### Requirement: Do not automatically reanalyze ordinary script saves
The system SHALL NOT create a new analysis task when a user later edits and saves a script version through the ordinary save flow.

#### Scenario: Save a later script edit
- **WHEN** a user saves changed script content after project creation
- **THEN** the system persists the script version
- **AND** does not automatically start the four-stage analysis
- **AND** leaves manual re-analysis as an explicit user action

#### Scenario: Reopen an already analyzed project
- **WHEN** a user reopens the production workbench after the initial analysis completed
- **THEN** the system returns the existing analysis result
- **AND** does not create or schedule another analysis task

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

### Requirement: Support retry from a failed stage
The system SHALL allow an authorized user to retry a failed stage without resetting successful earlier stages, and SHALL allow each workflow Agent to be explicitly rerun independently against its current required source.

#### Scenario: Retry failed intelligent splitting
- **WHEN** intelligent episode splitting fails and the user requests a retry
- **THEN** the system creates a new split Agent Run against the current script
- **AND** does not require or inject the latest global-understanding result

#### Scenario: Retry failed per-episode work
- **WHEN** a fan-out stage has failed episode units
- **THEN** the system retries only failed, missing, or stale units unless full regeneration was explicitly requested

### Requirement: Preserve structured intermediate results
The system SHALL retain bounded structured and raw diagnostic results for each Agent execution while treating successfully committed domain rows as the authoritative completed analysis output.

#### Scenario: Retrieve a completed analysis
- **WHEN** an authorized user opens current analysis details
- **THEN** the response includes stage status, Agent Run references, current action, error information, and formal result references
- **AND** the workbench reads completed global understanding, episodes, summaries, and assets from their formal repositories
- **AND** sensitive provider credentials are excluded

### Requirement: Determine stage completion from current formal coverage
The system SHALL consider a stage complete only when its current Agent contract and formal-data coverage checks pass for the current script state.

#### Scenario: Model returns valid-looking JSON without a save
- **WHEN** an Agent invocation returns content but never commits its required save tool
- **THEN** the stage does not succeed

#### Scenario: Current episode lacks required downstream data
- **WHEN** a summary or recognition stage is marked successful but a current active episode lacks the corresponding committed unit
- **THEN** the completion check rejects the stale stage state

### Requirement: Bind new Agent output to current script state
The new splitting, summary, and recognition Agents SHALL use trusted `script_id`, current source hashes, and current stable `episode_id` values as their business scope rather than historical `script_version_id` ownership.

#### Scenario: Ordinary script save is followed by explicit reanalysis
- **WHEN** a user edits the current script and explicitly reruns an Agent
- **THEN** the Agent reads the newly saved current source
- **AND** can replace the corresponding current formal output without requiring version rollback

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

