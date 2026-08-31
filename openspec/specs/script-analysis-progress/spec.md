# script-analysis-progress Specification

## Purpose
TBD - created by archiving change add-script-analysis-pipeline. Update Purpose after archive.
## Requirements
### Requirement: Display four independent stage progress indicators
The system SHALL display one progress indicator for each analysis stage, including percentage, human-readable status, and unit counts when a stage fans out by episode.

#### Scenario: Analysis is running
- **WHEN** at least one analysis stage is running
- **THEN** the UI displays the four stages in fixed order
- **AND** marks waiting, running, completed, partially failed, and failed stages distinctly

#### Scenario: Per-episode stage is running
- **WHEN** summary or recognition child Runs are active
- **THEN** the UI displays completed episodes over total snapshot episodes
- **AND** identifies the current or failed episode units without claiming provider-internal inference progress

#### Scenario: Episode splitting enters chunk fallback
- **WHEN** full-script splitting cannot complete for a capacity or incomplete-call reason
- **THEN** the UI continues to display one intelligent-splitting stage
- **AND** exposes fallback reason and completed chunks over total chunks without presenting chunks as episodes

#### Scenario: Analysis is complete
- **WHEN** all four stages pass their formal completion checks
- **THEN** every stage displays 100 percent
- **AND** the UI presents formal completed results

### Requirement: Show actionable interaction prompts
The system SHALL show a current action prompt for the active stage and actionable retry information for failed stage or episode units.

#### Scenario: Per-episode Agent is running
- **WHEN** a child Agent Run is processing an episode
- **THEN** the UI identifies the stage activity and episode number

#### Scenario: Some episode units fail
- **WHEN** a fan-out stage is partially failed
- **THEN** the UI displays the failed episode units and reasons
- **AND** provides an action to retry the unsuccessful units

### Requirement: Expose intermediate results for review
The system SHALL allow users to open committed formal stage results without waiting for all later stages to finish.

#### Scenario: Open completed episode split
- **WHEN** episode splitting commits while later stages are pending
- **THEN** the user can view formal episode IDs, numbers, titles, boundaries, and exact content
- **AND** later stages remain visibly pending

#### Scenario: Open partially completed recognition
- **WHEN** some recognition episode units have committed while others remain pending or failed
- **THEN** the user can view those formal characters, looks, scenes, props, prop states, and episode bindings
- **AND** no candidate confirmation is required for committed Agent output

### Requirement: Distinguish estimated model progress from completed work
The system SHALL derive fan-out progress from persisted terminal episode units and SHALL NOT claim provider-internal inference progress that is unavailable.

#### Scenario: Model inference has no granular progress
- **WHEN** a provider exposes no inference progress for a child Run
- **THEN** the UI displays its current action as running
- **AND** increments completed units only after the formal save commits

### Requirement: Restore progress after leaving the page
The system SHALL restore the latest current-script stage and episode-unit state when the user reopens the script workspace.

#### Scenario: Reopen an active fan-out analysis
- **WHEN** the user returns while summary or recognition is active
- **THEN** the UI loads persisted parent and child Run statuses and unit counts
- **AND** resumes polling only while work remains active

#### Scenario: Reopen a completed analysis
- **WHEN** the current formal coverage remains complete
- **THEN** the UI displays completed percentages and formal results
- **AND** does not restart any Agent

### Requirement: Keep the production workbench skeleton visible during initial loading
The system SHALL render the production workbench shell and script-page layout before project, script, and analysis data finish loading.

#### Scenario: Project and analysis data are loading
- **WHEN** the initial project-workbench requests are pending
- **THEN** the UI keeps the navigation, project header, script workspace, and analysis region in place
- **AND** uses skeleton or neutral placeholder content instead of a blank page or full-screen blocking spinner

