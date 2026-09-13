## MODIFIED Requirements

### Requirement: Element extraction delegates to focused workflow boundaries
The system SHALL delegate scoped formal extraction to focused preflight, Agent execution, normalization, persistence and finalization components. It SHALL remove the old raw JSON extraction and candidate-confirmation implementation.

#### Scenario: Facade validates before delegation
- **WHEN** extraction is requested for a project script
- **THEN** the system validates tenant membership, project access, permissions, selected asset scope, prompt policy and current source before invoking formal recognition

### Requirement: Element workflow behavior is covered by focused tests
The system SHALL test formal Agent persistence, deterministic matching, scope isolation, prompt policies, finalization, source changes and manual-data protection.

#### Scenario: Regression tests protect formal extraction
- **WHEN** backend element workflow tests run
- **THEN** they cover ALL, CHARACTER, SCENE and PROP scoped writes without candidate confirmation
- **AND** unauthorized, stale or out-of-scope saves are rejected atomically

### Requirement: Asset settings migrates away from legacy element extraction
Asset settings SHALL perform scoped formal recognition through preflight and confirmed submission. The old raw JSON element-extraction API SHALL be removed.

#### Scenario: Asset-settings user requests batch generation
- **WHEN** a user clicks batch generation on an asset-settings scope
- **THEN** the client performs preflight and submits formal recognition using the selected prompt policy
- **AND** it does not create a legacy candidate-normalization run

## REMOVED Requirements

### Requirement: Legacy extraction remains compatible during migration
**Reason**: Pre-release historical data can be cleared and all current consumers use formal recognition.
**Migration**: Remove legacy extraction and candidate-review endpoints, their consumers and obsolete records according to the cleanup manifest; use scoped formal recognition and normal asset editing.
