## ADDED Requirements

### Requirement: Platform administrators manage independent model billing prices
The system SHALL allow only authorized platform administrators to manage supplier cost prices and user point prices as independent rule sets for enabled platform models. Each rule set SHALL be selected by model, usage metric, normalized dimensions, and effective time.

#### Scenario: Administrator selects an enabled model
- **WHEN** an authorized platform administrator opens the model billing management page
- **THEN** the system SHALL provide a searchable dropdown containing enabled platform models with their name, Code, and Provider

#### Scenario: Non-platform user accesses billing management
- **WHEN** a user without the required platform billing permission requests a billing management API or page
- **THEN** the system SHALL deny the request and SHALL not expose supplier cost prices

### Requirement: Model cost and point prices are versioned automatically
The system SHALL create cost-price versions and point-price versions independently for each model, automatically assigning the next positive version number for that model and price type. Each version SHALL contain an effective start time, optional effective end time, and one or more metric price components.

#### Scenario: Administrator publishes a future cost price version
- **WHEN** an authorized platform administrator publishes valid cost components for a selected model and future effective start time
- **THEN** the system SHALL assign the next cost-price version number, persist the version and components, and return the assigned version number

#### Scenario: Administrator publishes a point price version
- **WHEN** an authorized platform administrator publishes valid point components for a selected model and effective start time
- **THEN** the system SHALL assign the next point-price version number independently from cost-price versions and persist the version

#### Scenario: Effective periods overlap
- **WHEN** an administrator submits a version whose effective interval overlaps an existing non-revoked version of the same model and price type
- **THEN** the system SHALL reject the publication without changing existing versions

### Requirement: Price versions have an immutable effective lifecycle
The system SHALL keep published price components immutable. A version that has not reached its effective start time MAY be revoked by an authorized platform administrator; a currently effective or expired version SHALL remain readable and cannot be edited or revoked.

#### Scenario: Administrator revokes a future version
- **WHEN** an authorized platform administrator revokes a version before its effective start time
- **THEN** the system SHALL mark the version revoked and SHALL exclude it from future price resolution

#### Scenario: Administrator attempts to change an effective version
- **WHEN** an authorized platform administrator attempts to edit or revoke a version at or after its effective start time
- **THEN** the system SHALL reject the operation and require a later replacement version

### Requirement: Billing components define measurable pricing units
The system SHALL support per-call, token, image, video-second, character, and other registered usage metrics. A cost component SHALL include a positive unit size, non-negative currency unit price, and currency; a point component SHALL include a positive unit size and non-negative point rate.

#### Scenario: Invalid billing component is published
- **WHEN** an administrator submits an unsupported metric, non-positive unit size, negative rate, or blank currency for a cost component
- **THEN** the system SHALL reject the whole version without persisting any component

### Requirement: Platform users can review billing version history
The system SHALL present current, future, historical, and revoked cost and point price versions separately for each model, including effective period and component details. The interface SHALL expose publish and revoke controls only when the user's permissions and version state allow them.

#### Scenario: Administrator reviews model billing history
- **WHEN** an authorized platform administrator selects a model on the billing management page
- **THEN** the system SHALL show both cost-price and point-price version histories with their metrics, units, rates, effective periods, and lifecycle status
