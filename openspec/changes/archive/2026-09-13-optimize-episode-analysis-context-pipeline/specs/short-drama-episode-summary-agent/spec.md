## MODIFIED Requirements

### Requirement: Load summary Skills and only the required tools
The Agent SHALL load short-drama-analysis-foundation followed by short-drama-episode-summary-framework and snapshot both. The server SHALL perform and audit the trusted read_current_episode preflight before the first model request. Only read_current_episode and save_episode_summary SHALL be executable for this stage, even when a shared provider schema describes additional tools. The contract SHALL require the trusted read followed by a successful save without requiring the model to request the initial read.

#### Scenario: Model attempts to finish without a save
- **WHEN** trusted preloading completed but save_episode_summary has not succeeded
- **THEN** the Run cannot succeed and its output is not treated as formal data

#### Scenario: First request uses preloaded content
- **WHEN** the summary model is invoked
- **THEN** current source and evidence state are already available and the model can directly submit the terminal save
