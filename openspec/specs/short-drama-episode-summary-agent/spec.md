# short-drama-episode-summary-agent Specification

## Purpose
TBD - created by archiving change add-remaining-short-drama-analysis-agents. Update Purpose after archive.
## Requirements
### Requirement: Provide an independently runnable episode-summary Agent
The system SHALL provide an enabled workflow Agent identified by `short-drama-episode-summary` that can generate a formal summary for one trusted current active episode per Run.

#### Scenario: Run the Agent for one episode
- **WHEN** a summary child Run starts with an authorized active `episode_id`
- **THEN** it reads and summarizes only that episode's current formal content
- **AND** does not require global understanding or a legacy summary-stage JSON result

### Requirement: Load summary Skills and only the required tools
The Agent SHALL load `short-drama-analysis-foundation` followed by `short-drama-episode-summary-framework`, expose only `read_current_episode` and `save_episode_summary`, and require those calls in that order.

#### Scenario: Model attempts to finish without a save
- **WHEN** the model has read the episode but has not successfully called `save_episode_summary`
- **THEN** the Run cannot succeed
- **AND** its output is not treated as formal summary data

### Requirement: Produce the formal episode-summary contract
The Agent SHALL produce a non-blank chronological `summary`, an array of two to five non-duplicative `highlights`, and a nullable `endingHook` based only on the current episode.

#### Scenario: Episode has a clear ending hook
- **WHEN** the episode ends with an evidenced suspense, reversal, or follow-up motive
- **THEN** the saved document contains that ending in `endingHook`

#### Scenario: Episode has no clear ending hook
- **WHEN** the episode contains no evidenced ending hook
- **THEN** the Agent saves `endingHook` as null
- **AND** does not invent one

### Requirement: Persist one extensible current summary per episode
The system SHALL store the current summary in `script_episode_summary`, uniquely related to `episode_id`, with `schema_version`, structured `content_json`, source, generating Run, and timestamps.

#### Scenario: Save a summary for the first time
- **WHEN** a valid summary is saved for an episode without a formal summary row
- **THEN** the tool inserts one row containing `summary`, `highlights`, and `endingHook`

#### Scenario: Regenerate an existing summary
- **WHEN** an authorized explicit regeneration saves a new valid document for the same episode
- **THEN** the tool replaces the complete current document for that `episode_id`
- **AND** does not create a second active summary row

### Requirement: Protect summary saves with trusted episode state
The save tool SHALL derive business identity from Run scope and reject inactive, foreign, unread, or changed episodes before persistence.

#### Scenario: Episode content changes during generation
- **WHEN** the episode fingerprint no longer matches the server-recorded read state
- **THEN** the save fails with a stale-source error
- **AND** the previous formal summary remains unchanged

### Requirement: Keep legacy summary reads compatible during migration
The system SHALL mirror the formal summary text to the legacy `script_episode.summary` field while treating `script_episode_summary.content_json` as authoritative for new reads and edits.

#### Scenario: Legacy consumer reads an updated summary
- **WHEN** the new save tool commits a summary
- **THEN** the compatibility summary text is updated in the same transaction
- **AND** highlights and ending hook remain available from the formal summary document
