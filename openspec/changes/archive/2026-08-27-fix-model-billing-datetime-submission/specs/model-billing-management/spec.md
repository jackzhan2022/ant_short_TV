## MODIFIED Requirements

### Requirement: Model cost and point prices are versioned automatically
The system SHALL create cost-price versions and point-price versions independently for each model, automatically assigning the next positive version number for that model and price type. Each version SHALL contain an effective start time, optional effective end time, and one or more metric price components. The model billing management interface SHALL submit selected effective times as ISO local date-time values accepted by the publication APIs without changing the selected local wall-clock time.

#### Scenario: Administrator publishes a future cost price version
- **WHEN** an authorized platform administrator publishes valid cost components for a selected model and future effective start time
- **THEN** the system SHALL assign the next cost-price version number, persist the version and components, and return the assigned version number

#### Scenario: Administrator publishes a point price version
- **WHEN** an authorized platform administrator publishes valid point components for a selected model and effective start time
- **THEN** the system SHALL assign the next point-price version number independently from cost-price versions and persist the version

#### Scenario: Model billing interface submits selected effective times
- **WHEN** an authorized platform administrator selects effective start and optional end times in either model billing publication form
- **THEN** the interface SHALL submit both present values as ISO local date-time strings while preserving their selected local date and time

#### Scenario: Effective periods overlap
- **WHEN** an administrator submits a version whose effective interval overlaps an existing non-revoked version of the same model and price type
- **THEN** the system SHALL reject the publication without changing existing versions
