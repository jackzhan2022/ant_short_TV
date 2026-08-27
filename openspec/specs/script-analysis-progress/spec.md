# script-analysis-progress Specification

## Purpose
TBD - created by archiving change add-script-analysis-pipeline. Update Purpose after archive.
## Requirements
### Requirement: Display four independent stage progress indicators
The system SHALL display one progress indicator for each analysis stage, including a percentage and a human-readable status.

#### Scenario: First creation with initial script is loading
- **WHEN** the user is redirected to the production workbench after creating a project with initial script content
- **THEN** the workbench keeps its page skeleton visible
- **AND** displays the four analysis stages when an active initial analysis task exists
- **AND** shows each stage percentage and current status

#### Scenario: Project has no initial script
- **WHEN** the user creates a project without initial script content
- **THEN** the workbench renders its normal skeleton and empty script state
- **AND** does not display an active analysis loading state

#### Scenario: Analysis is running
- **WHEN** at least one analysis stage is running
- **THEN** the UI displays the four stages in fixed order
- **AND** displays each stage percentage
- **AND** marks waiting, running, completed, and failed stages distinctly

#### Scenario: Analysis is complete
- **WHEN** all four stages succeed
- **THEN** every stage displays 100 percent
- **AND** the UI presents the completed analysis results

### Requirement: Show actionable interaction prompts
The system SHALL show a current action prompt for the active stage and an actionable message for failed stages.

#### Scenario: Stage is running
- **WHEN** a stage is processing
- **THEN** the UI describes the current activity, such as understanding the story, locating episode boundaries, extracting summaries, or recognizing characters and scenes

#### Scenario: Stage fails
- **WHEN** a stage fails
- **THEN** the UI displays the failure reason
- **AND** provides an action to retry that stage

### Requirement: Expose intermediate results for review
The system SHALL allow users to open completed stage results without waiting for all later stages to finish.

#### Scenario: Open completed episode split
- **WHEN** episode splitting completes while later stages are pending
- **THEN** the user can view episode numbers, titles, boundaries, and content
- **AND** later stages remain visibly pending

#### Scenario: Review recognized assets
- **WHEN** character and scene recognition completes
- **THEN** the user can review recognized characters, scenes, and props
- **AND** can confirm eligible results through the existing confirmation workflow

### Requirement: Distinguish estimated model progress from completed work
The system SHALL treat progress percentages as stage progress and SHALL NOT claim provider-internal inference progress that is unavailable.

#### Scenario: Model inference has no granular progress
- **WHEN** the provider does not expose inference progress
- **THEN** the UI displays a monotonic stage estimate and current action
- **AND** sets the stage to 100 percent only after a valid result is persisted

### Requirement: Restore progress after leaving the page
The system SHALL restore the latest server-side analysis state when the user reopens the script workspace.

#### Scenario: Reopen an active analysis
- **WHEN** the user returns to a script version with an active analysis task
- **THEN** the UI loads the latest stage statuses and percentages
- **AND** resumes polling only while a stage is active

#### Scenario: Reopen a completed analysis
- **WHEN** the user returns to a project whose initial analysis is complete
- **THEN** the UI displays the completed percentages and results
- **AND** does not restart analysis or show an active loading state

### Requirement: Keep the production workbench skeleton visible during initial loading
The system SHALL render the production workbench shell and script-page layout before project, script, and analysis data finish loading.

#### Scenario: Project and analysis data are loading
- **WHEN** the initial project-workbench requests are pending
- **THEN** the UI keeps the navigation, project header, script workspace, and analysis region in place
- **AND** uses skeleton or neutral placeholder content instead of a blank page or full-screen blocking spinner

