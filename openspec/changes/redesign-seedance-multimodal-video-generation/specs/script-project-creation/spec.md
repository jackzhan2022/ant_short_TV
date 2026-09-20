## MODIFIED Requirements

### Requirement: Project initialization fields
The system SHALL collect and persist the project initialization fields required by the creation flow, including aspect ratio, file format, script type, breakdown strength, cover, visual style, default video resolution, default generated-audio behavior, and default video-watermark behavior.

#### Scenario: User configures the project
- **WHEN** the user reaches the second step
- **THEN** the system shows the initialization fields with defaults preselected where applicable
- **AND** defaults video resolution to `720p`, generated audio to enabled, and watermark to disabled
- **AND** the user can change the values before creating the project

#### Scenario: User submits the form
- **WHEN** the user submits the completed creation flow
- **THEN** the system creates the project with the selected initialization and video-generation default values and returns the created project for navigation

### Requirement: Existing project creation callers remain supported
The system SHALL continue accepting project creation requests that omit video resolution, generated-audio, or watermark defaults.

#### Scenario: Existing caller creates project
- **WHEN** an existing client submits a project request without the new video-default fields
- **THEN** the system creates the project with `720p` resolution, generated audio enabled, and watermark disabled
