# scoped-asset-reextraction Specification

## Purpose
TBD - created by archiving change scope-agent-asset-extraction. Update Purpose after archive.
## Requirements
### Requirement: Start scoped formal asset re-extraction from asset settings
The system SHALL let an authorized asset-settings user submit formal asset re-extraction for `ALL`, `CHARACTER`, `SCENE`, or `PROP`, using the per-episode asset-recognition Agent and formal asset-write tool rather than the legacy raw JSON element-extraction path.

#### Scenario: Re-extract only scenes
- **WHEN** a user confirms `SCENE` re-extraction from asset settings
- **THEN** the system creates an asynchronous scoped recognition operation for the frozen active episodes
- **AND** each child Run invokes the formal Agent with `SCENE` scope
- **AND** the asset-settings client does not call `scripts/ai-extract-elements`

#### Scenario: Re-extract all asset types
- **WHEN** a user submits `ALL` re-extraction
- **THEN** the system recognizes characters, character looks, scenes, props, and prop states for every frozen active episode
- **AND** the operation exposes persisted progress and terminal status

### Requirement: Preflight existing scoped asset impact before submission
The system SHALL provide a preflight result for a selected scope that reports existing canonical assets, visual variants, and non-empty prompts that can be affected by scoped re-extraction.

#### Scenario: Scope has no existing content
- **WHEN** a user requests preflight for a scope with no existing canonical assets, variants, or non-empty prompts
- **THEN** the client submits re-extraction directly with the default `FILL_EMPTY` prompt policy

#### Scenario: Scope has existing content
- **WHEN** a user requests preflight for a scope containing existing assets, variants, or non-empty prompts
- **THEN** the client presents the reported counts and requires explicit confirmation before submitting the operation

### Requirement: Require explicit prompt regeneration policy
The confirmation interface SHALL default to `FILL_EMPTY` and SHALL offer `REGENERATE_ALL` only as an explicit user selection for the submitted scope.

#### Scenario: Confirm only missing prompts
- **WHEN** a user confirms re-extraction without changing the default prompt policy
- **THEN** the operation generates prompts only for new or empty canonical assets and variants in the selected scope
- **AND** existing non-empty prompts remain unchanged

#### Scenario: Confirm prompt regeneration
- **WHEN** a user explicitly selects `REGENERATE_ALL` and confirms
- **THEN** the operation regenerates prompts only for the selected scope
- **AND** the confirmation interface reports the number of existing non-empty prompts that can be replaced

### Requirement: Surface scoped operation failures
The asset-settings interface SHALL display the terminal failure reason returned by the scoped operation and SHALL clear its loading state on every terminal status.

#### Scenario: Agent tool validation fails
- **WHEN** a scoped child Run fails validation
- **THEN** the parent operation reaches a failed terminal status with an actionable reason
- **AND** the asset-settings interface displays that reason rather than only a generic extraction failure

