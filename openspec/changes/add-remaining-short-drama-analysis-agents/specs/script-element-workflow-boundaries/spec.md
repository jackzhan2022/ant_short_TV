## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Extracted drafts replace previous unconfirmed drafts
**Reason**: The new fourth Agent saves validated current results directly as formal editable data and reconciles Agent-managed episode bindings instead of replacing project-wide unconfirmed drafts.

**Migration**: Preserve legacy draft rows and APIs for old extraction calls; route new `short-drama-asset-recognition` saves through the formal script-scoped path.

### Requirement: Name-based merge targets are prepared during extraction
**Reason**: The new path resolves trusted keys, exact normalized names, and explicit aliases transactionally and rejects ambiguous matches instead of creating pending merge targets.

**Migration**: Retain existing `merge_target_id` and candidate evidence for legacy records; new Agent Runs record deterministic match evidence in Run/tool provenance.

### Requirement: User confirmation applies merge updates
**Reason**: Successful fourth-Agent output is required to become formal editable data immediately, so stage completion cannot wait for manual confirmation.

**Migration**: Existing candidates can still be confirmed through the legacy workflow, while new Agent-created formal assets are edited through current asset APIs.

