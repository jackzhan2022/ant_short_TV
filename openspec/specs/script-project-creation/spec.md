# script-project-creation Specification

## Purpose
TBD - created by archiving change enhance-script-project-creation. Update Purpose after archive.
## Requirements
### Requirement: Independent short-drama creation menu entry
The system SHALL provide an independent main navigation entry named `短剧创作` for the short-drama creation experience.

#### Scenario: User views main navigation
- **WHEN** the user has permission to create or use projects
- **THEN** the main navigation shows a `短剧创作` menu entry
- **THEN** selecting it opens the short-drama creation page

#### Scenario: User opens short-drama creation page
- **WHEN** the user navigates to the `短剧创作` page
- **THEN** the page shows the script intake area and the inspiration gallery list together on the first screen

### Requirement: Guided project creation flow
The system SHALL present project creation as a two-step flow that begins with inspiration and script intake and ends with project initialization configuration before submission.

#### Scenario: User opens create project
- **WHEN** the user starts a new project from the `短剧创作` menu entry
- **THEN** the system shows the first step with script input or skip actions and an inspiration gallery below it
- **THEN** the system does not submit a project until the second step is completed

#### Scenario: User advances after choosing to skip script input
- **WHEN** the user chooses to skip script input
- **THEN** the system allows them to continue to the configuration step with an empty script draft

### Requirement: Script intake and inspiration browsing
The system SHALL let the user paste script text or upload a supported script file on the first step, and SHALL show inspiration items that can be browsed independently of whether script content is present.

#### Scenario: User pastes script content
- **WHEN** the user pastes script text into the first step
- **THEN** the system keeps the script content available for later creation steps and related inspiration browsing

#### Scenario: User uploads a supported script file
- **WHEN** the user uploads a supported script file on the first step
- **THEN** the system accepts the file and uses its content as the current script draft

#### Scenario: User has no script content
- **WHEN** the script input is empty
- **THEN** the system still shows a usable inspiration gallery with default curated items

#### Scenario: User switches inspiration category
- **WHEN** the user switches between inspiration categories
- **THEN** the gallery updates to show the selected category's items without losing the current script draft

### Requirement: Project initialization fields
The system SHALL collect and persist the project initialization fields required by the new creation flow, including aspect ratio, file format, script type, breakdown strength, cover, and visual style.

#### Scenario: User configures the project
- **WHEN** the user reaches the second step
- **THEN** the system shows the initialization fields with defaults preselected where applicable
- **THEN** the user can change the values before creating the project

#### Scenario: User submits the form
- **WHEN** the user submits the completed creation flow
- **THEN** the system creates the project with the selected initialization values and returns the created project for navigation

### Requirement: Existing project creation callers remain supported
The system SHALL continue accepting legacy project creation requests that provide only the existing core project fields.

#### Scenario: Legacy caller creates project
- **WHEN** an existing client submits a project request without the new initialization fields
- **THEN** the system still creates the project successfully using defaults for missing optional values

