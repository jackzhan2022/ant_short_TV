## ADDED Requirements

### Requirement: Workbench displays project metadata dynamically
The production workbench SHALL display project metadata from the project detail response instead of fixed values when the corresponding fields are present.

#### Scenario: Display configured project settings
- **WHEN** the project has aspect ratio, file format, script type, breakdown strength, or visual style values
- **THEN** the workbench displays the mapped human-readable values for those fields

#### Scenario: Missing project metadata
- **WHEN** one of the project metadata fields is null or unsupported
- **THEN** the workbench displays a neutral placeholder or the raw value without blocking the page

### Requirement: Workbench displays the current team's global point balance
The production workbench SHALL query the active tenant's point account and display its available `balance` as the global team balance, independent of the current project.

#### Scenario: Team balance is available
- **WHEN** the active tenant point-account request succeeds
- **THEN** the header displays the returned available balance

#### Scenario: Team balance request fails
- **WHEN** the point-account request fails or returns no account
- **THEN** the header displays a neutral placeholder and the project and script workspace remain usable

### Requirement: Workbench does not invent unsupported dynamic fields
The workbench SHALL retain only fields that have no authoritative backend source as static UI content, including the platform disclaimer, navigation labels, and the current resolution label when no project resolution field exists.

#### Scenario: Existing project data replaces hard-coded values
- **WHEN** a previously hard-coded field has an authoritative project or tenant response field
- **THEN** the UI uses that response field rather than the old literal value

#### Scenario: No authoritative source exists
- **WHEN** the UI field has no project or tenant data source
- **THEN** the UI may retain the current static value without pretending it is project-specific data
