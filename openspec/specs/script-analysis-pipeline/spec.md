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
The system SHALL execute the stages in this order: global story understanding, intelligent episode splitting, episode summary extraction, and character/scene recognition.

#### Scenario: Advance after successful stage
- **WHEN** a stage produces a valid result
- **THEN** the system marks that stage succeeded
- **AND** starts the next stage with the previous result as input

#### Scenario: Preserve failed stage
- **WHEN** a stage fails
- **THEN** the system marks the stage failed with an actionable error
- **AND** does not mark later stages successful
- **AND** preserves all earlier successful results

### Requirement: Isolate results by script version
The system SHALL bind every analysis task and result to the script version that triggered it.

#### Scenario: Submit a newer version while analysis is running
- **WHEN** a user submits a newer script version before the prior analysis completes
- **THEN** the system creates an independent analysis task for the newer version
- **AND** results from the older task SHALL NOT overwrite the newer version

### Requirement: Support retry from a failed stage
The system SHALL allow an authorized user to retry a failed stage without resetting successful earlier stages.

#### Scenario: Retry failed intelligent splitting
- **WHEN** intelligent episode splitting fails and the user requests a retry
- **THEN** the system creates or resumes an execution attempt for that stage
- **AND** reuses the latest successful global understanding
- **AND** leaves later stages waiting until splitting succeeds

### Requirement: Preserve structured intermediate results
The system SHALL store the structured result and raw diagnostic response for each completed AI stage.

#### Scenario: Retrieve a completed analysis
- **WHEN** an authorized user opens analysis details for a script version
- **THEN** the response includes each stage status, result, current action, error information, and associated execution metadata
- **AND** sensitive provider credentials are excluded

