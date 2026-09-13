## MODIFIED Requirements

### Requirement: Load recognition Skills and only the required tools
The Agent SHALL load short-drama-analysis-foundation followed by short-drama-asset-recognition-framework. The server SHALL perform and audit read_current_episode before the first model invocation, including trusted source state and compact asset catalog. Only read_current_episode and save_episode_assets SHALL be executable in this stage, regardless of shared provider tool descriptions. Successful trusted read and terminal save SHALL remain required in order; generated episode summary SHALL NOT be supplied.

#### Scenario: Preserve recognition configuration
- **WHEN** a recognition Run is created
- **THEN** it snapshots both Skills, the model, shared schema revision and its exact read/save execution permissions

#### Scenario: Recognition without summary
- **WHEN** the current episode has no completed summary
- **THEN** recognition proceeds from preloaded trusted source with dynamic assets after the frozen prefix
