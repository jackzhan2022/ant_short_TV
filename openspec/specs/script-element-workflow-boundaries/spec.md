# script-element-workflow-boundaries Specification

## Purpose
TBD - created by archiving change refactor-script-workflow-boundaries. Update Purpose after archive.
## Requirements
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

### Requirement: Agent recognition writes formal script-scoped assets
The new asset-recognition Agent path SHALL write valid normalized characters, scenes, props, character looks, prop states, and episode bindings directly as formal editable data scoped to the current script.

#### Scenario: Recognition payload is valid and deterministic
- **WHEN** `save_episode_assets` validates the complete episode payload and resolves every identity
- **THEN** it commits formal data without waiting for candidate confirmation
- **AND** the recognition child Run can succeed

### Requirement: Direct persistence retains defensive normalization
The direct Agent path SHALL retain schema validation, normalized names, explicit aliases, source evidence, raw diagnostics, tenant/project/script isolation, and deterministic duplicate prevention before formal persistence.

#### Scenario: Provider returns an unsupported item shape
- **WHEN** an item cannot be normalized to its required object contract
- **THEN** the complete save call fails before canonical insertion
- **AND** diagnostic evidence remains associated with the Agent Run

### Requirement: Asset settings migrates away from legacy element extraction
Asset settings SHALL perform scoped formal recognition through preflight and confirmed submission. The old raw JSON element-extraction API SHALL be removed.

#### Scenario: Asset-settings user requests batch generation
- **WHEN** a user clicks batch generation on an asset-settings scope
- **THEN** the client performs preflight and submits formal recognition using the selected prompt policy
- **AND** it does not create a legacy candidate-normalization run

