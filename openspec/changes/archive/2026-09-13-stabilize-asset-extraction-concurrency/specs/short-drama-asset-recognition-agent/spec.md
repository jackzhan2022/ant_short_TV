## MODIFIED Requirements

### Requirement: Load recognition Skills and only the required tools
The Agent SHALL load short-drama-analysis-foundation followed by short-drama-asset-recognition-framework and expose only read_current_episode, search_script_assets, read_asset_details and save_episode_assets. It SHALL read the current episode first, optionally search or load authorized details, and finish with formal asset saving. The Skill SHALL require lookup before proposing an identity absent from the candidate subset and reuse trusted matching keys.

#### Scenario: Preserve recognition configuration
- **WHEN** the Agent Run is created
- **THEN** it snapshots both Skills, the model configuration, and exactly the episode-read, catalog-search, detail-read and asset-save tools

#### Scenario: Search is needed before saving
- **WHEN** a recognized entity has no trustworthy key in the initial candidate subset
- **THEN** the Agent searches the scoped global catalog before proposing a new identity and can load matching details before saving

#### Scenario: Initial candidates suffice
- **WHEN** all recognized entities match trusted initial candidates
- **THEN** the Agent can save immediately after reading the episode without mandatory extra lookup calls
