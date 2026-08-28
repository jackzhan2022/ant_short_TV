# script-analysis-state-container Specification

## Purpose
Define the script page state container shown while episode analysis is pending, running, retrying, or failed, and the transition back to the script workbench after completion.
## Requirements
### Requirement: Analysis state owns the pre-completion page
The script page SHALL render a dedicated analysis state container as its only main content while the current script analysis is pending, running, or retrying.

#### Scenario: Analysis is in progress
- **WHEN** the current analysis status is `PENDING`, `RUNNING`, or `RETRYING`
- **THEN** the page preserves the top information skeleton and displays the current-episode parsing message, wait guidance, and four analysis stages

#### Scenario: Workbench is withheld
- **WHEN** analysis is not `COMPLETED`
- **THEN** the script body, outline, episode editor, and completion-only controls are not mounted

### Requirement: Completed analysis reveals the workbench
The script page SHALL render the existing script workbench after analysis reaches `COMPLETED`.

#### Scenario: Analysis completes after polling
- **WHEN** a refresh observes analysis status `COMPLETED`
- **THEN** the state container is replaced by the existing script body, outline, and episode content

### Requirement: Failed analysis is actionable
The analysis state container SHALL show the failure reason and a retry action when analysis status is `FAILED` and retry is available.

#### Scenario: Analysis fails
- **WHEN** the analysis status is `FAILED`
- **THEN** the page shows the error message and allows retrying the failed analysis according to the existing retry contract
