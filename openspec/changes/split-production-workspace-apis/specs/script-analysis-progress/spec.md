## MODIFIED Requirements

### Requirement: Restore progress after leaving the page
The system SHALL restore the latest current-script stage and episode-unit state when the user reopens the script workspace, and SHALL poll only the lightweight current-analysis contract while work remains active.

#### Scenario: Reopen an active fan-out analysis
- **WHEN** the user returns while summary or recognition is active
- **THEN** the UI loads persisted parent and child Run statuses and unit counts from the focused script-page workspace
- **AND** resumes polling through the current-analysis endpoint only while work remains active
- **AND** does not reload script bodies, versions, assets, or storyboards on each poll

#### Scenario: Analysis reaches a terminal state
- **WHEN** status polling observes completion or failure
- **THEN** the UI stops polling
- **AND** performs at most one focused script-page refresh when committed page data must be reconciled

#### Scenario: Reopen a completed analysis
- **WHEN** the current formal coverage remains complete
- **THEN** the UI displays completed percentages and formal results
- **AND** does not restart any Agent or polling timer

#### Scenario: Assemble current analysis for multiple stages
- **WHEN** the focused script workspace or current-analysis endpoint returns a task with multiple stages
- **THEN** related results, runs, fan-out state, and split state are loaded with a bounded number of bulk reads
- **AND** the number of database reads does not grow linearly with the number of stages
- **AND** stage order and persisted status semantics remain unchanged
