## MODIFIED Requirements

### Requirement: Execute the four analysis stages in order
The system SHALL execute the stages in this order: global story understanding, intelligent episode splitting, episode summary extraction, and character/scene/prop recognition. Each stage SHALL invoke its enabled workflow Agent, and the latter three Agents SHALL read their own current trusted source rather than consume a prior stage's normalized JSON as model input.

#### Scenario: Advance after committed formal output
- **WHEN** a stage completes its required terminal save contract and formal coverage validation
- **THEN** the system marks that stage succeeded
- **AND** starts the next configured stage

#### Scenario: Preserve failed stage
- **WHEN** a stage fails before its formal completion condition
- **THEN** the system marks that stage failed or partially failed with an actionable error
- **AND** does not mark later stages successful
- **AND** preserves all earlier committed formal data

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

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Isolate results by script version
**Reason**: The three new Agents are explicitly current-state, independently rerunnable workflows whose formal output belongs to the script and stable episodes rather than immutable script-version analysis snapshots.

**Migration**: Retain existing `script_version_id` columns and legacy task/result records for compatibility and diagnostics, but make current script/episode hashes authoritative for new Agent saves and stale-write protection.

