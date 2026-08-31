# script-element-workflow-boundaries Specification

## Purpose
TBD - created by archiving change refactor-script-workflow-boundaries. Update Purpose after archive.
## Requirements
### Requirement: Element extraction delegates to focused workflow boundaries
The system SHALL keep the existing script element extraction API behavior while separating AI extraction, draft persistence, and confirmation responsibilities into focused backend components.

#### Scenario: Existing extraction endpoint remains compatible
- **WHEN** a user requests script element extraction through the existing project script API
- **THEN** the system returns the existing script workspace response shape without requiring frontend API changes

#### Scenario: Facade validates before delegation
- **WHEN** extraction is requested for a project script
- **THEN** the system validates tenant membership, project access, permissions, element type, and script content before invoking the element extraction component

### Requirement: Element workflow behavior is covered by focused tests
The system SHALL include backend tests that verify extraction persistence, draft replacement, merge target preparation, confirmation merge updates, and isolation by tenant, project, and element type.

#### Scenario: Regression tests protect extraction and confirmation rules
- **WHEN** backend tests are run for the script element workflow
- **THEN** they cover both AI extraction success with visible workspace data and user confirmation paths for all supported element types

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

### Requirement: Legacy extraction remains compatible during migration
Existing non-Agent extraction and candidate-review APIs SHALL remain readable and operable until their consumers migrate, while new analysis-stage completion SHALL not depend on their review decisions.

#### Scenario: Existing client opens a legacy candidate review
- **WHEN** legacy candidate data exists
- **THEN** the existing review endpoint continues to expose it
- **AND** it does not replace or block current formal Agent data

