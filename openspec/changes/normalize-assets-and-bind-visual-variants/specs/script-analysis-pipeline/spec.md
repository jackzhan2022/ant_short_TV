## MODIFIED Requirements

### Requirement: Execute the four analysis stages in order
The system SHALL execute the stages in this order: global story understanding, intelligent episode splitting, episode summary extraction, and character/scene recognition. The character/scene recognition stage SHALL complete response normalization and candidate persistence before it can succeed.

#### Scenario: Advance after successful stage
- **WHEN** a stage produces a valid result
- **THEN** the system marks that stage succeeded
- **AND** starts the next stage with the previous result as input

#### Scenario: Preserve failed stage
- **WHEN** a stage fails
- **THEN** the system marks the stage failed with an actionable error
- **AND** does not mark later stages successful
- **AND** preserves all earlier successful results

#### Scenario: Recognition output cannot be normalized
- **WHEN** the recognition provider call succeeds but required asset fields cannot be normalized
- **THEN** the system marks the recognition stage as a business failure with candidate diagnostics
- **AND** does not write malformed canonical assets

### Requirement: Preserve structured intermediate results
The system SHALL store the structured result and raw diagnostic response for each completed AI stage. For character/scene recognition, the system SHALL also retain normalized output, normalization-run status, candidate diagnostics, and merge evidence.

#### Scenario: Retrieve a completed analysis
- **WHEN** an authorized user opens analysis details for a script version
- **THEN** the response includes each stage status, result, current action, error information, and associated execution metadata
- **AND** sensitive provider credentials are excluded

#### Scenario: Retrieve recognition diagnostics
- **WHEN** an authorized user opens a completed or failed character/scene recognition stage
- **THEN** the response distinguishes raw provider output, normalized candidates, validation failures, and proposed merge decisions
- **AND** does not expose provider credentials or unrestricted secret configuration
