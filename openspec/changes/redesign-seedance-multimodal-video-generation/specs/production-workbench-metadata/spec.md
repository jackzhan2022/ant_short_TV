## MODIFIED Requirements

### Requirement: Workbench displays project metadata dynamically
The production workbench SHALL display project metadata from the project detail response instead of fixed values when the corresponding fields are present, including aspect ratio, video resolution, generated-audio default, and watermark default.

#### Scenario: Display configured project settings
- **WHEN** the project has aspect ratio, file format, script type, breakdown strength, visual style, or video-default values
- **THEN** the workbench displays the mapped human-readable values for those fields

#### Scenario: Missing project video defaults
- **WHEN** a historical project omits one or more video-default values
- **THEN** the workbench uses `720p`, generated audio enabled, and watermark disabled without blocking the page

### Requirement: Workbench does not invent unsupported dynamic fields
The workbench SHALL retain only fields that have no authoritative backend source as static UI content. Aspect ratio, resolution, generated-audio behavior, watermark behavior, and selected video Model MUST use authoritative project, Model, or task data rather than fixed literals.

#### Scenario: Existing project data replaces hard-coded values
- **WHEN** a previously hard-coded field has an authoritative project, Model, task, or tenant response field
- **THEN** the UI uses that response field rather than the old literal value

#### Scenario: No authoritative source exists
- **WHEN** a UI field has no project, Model, task, or tenant data source
- **THEN** the UI may retain the current static value without presenting it as project-specific data

## ADDED Requirements

### Requirement: Storyboard generation controls use effective model settings
The storyboard workbench SHALL load enabled project video Models, submit the selected `modelId`, inherit project defaults, and allow supported per-task duration, resolution, generated-audio, and watermark overrides without changing the storyboard-card layout.

#### Scenario: User creates a video with inherited settings
- **WHEN** the user starts generation without changing task settings
- **THEN** the request uses the selected or project-default Model, project aspect ratio, project resolution, project generated-audio setting, project watermark setting, and storyboard planned duration

#### Scenario: User switches to a model that does not support the selected resolution
- **WHEN** the current resolution is unsupported by the newly selected Model
- **THEN** the workbench changes the task resolution to `720p` and visibly informs the user

#### Scenario: Batch generation contains an invalid storyboard
- **WHEN** one storyboard fails prompt or media validation during batch generation
- **THEN** valid sibling storyboards remain eligible for submission and the invalid storyboard reports its own failure
