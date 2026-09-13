## ADDED Requirements

### Requirement: Logical assets are independent from visual variants
The system SHALL preserve each character, scene, and prop as a canonical logical asset and SHALL store generated or uploaded visual representations as independently identified variants owned by that asset.

#### Scenario: Create the first visual variant
- **WHEN** an authorized user creates or generates a visual representation for a canonical asset
- **THEN** the system creates a visual variant linked to that asset without duplicating the canonical asset
- **AND** the variant records its source and generation status

#### Scenario: Create another appearance
- **WHEN** an authorized user adds a costume, alternate scene appearance, or alternate prop appearance
- **THEN** the system creates another variant under the same canonical asset
- **AND** both variants remain independently addressable

### Requirement: Canonical assets expose a primary visual fallback
The system SHALL allow one active primary visual variant per canonical asset and SHALL use it when no episode-specific preferred binding exists.

#### Scenario: Select a primary variant
- **WHEN** an authorized user marks one active variant as primary
- **THEN** the system clears the prior primary marker for the same canonical asset
- **AND** returns the selected variant as the asset's primary visual representation

#### Scenario: Primary variant becomes unavailable
- **WHEN** the primary variant is deleted, disabled, or has no usable media result
- **THEN** the system does not return it as a usable visual reference
- **AND** exposes that the asset requires another primary variant or generation result

### Requirement: Visual variants have an explicit lifecycle
The system SHALL expose variant source, generation status, current media result, error details, and timestamps independently from canonical asset confirmation status.

#### Scenario: Variant generation fails
- **WHEN** image generation for one variant fails
- **THEN** the variant records the failed generation status and normalized error
- **AND** the canonical asset and its other variants remain usable

#### Scenario: Variant generation succeeds
- **WHEN** image generation completes with a valid result
- **THEN** the variant references the current result and reports a completed status

### Requirement: Existing asset media remains compatible during migration
The system SHALL preserve existing confirmed assets and current media references while visual variants are introduced.

#### Scenario: Read a migrated asset
- **WHEN** an existing asset has a legacy current image reference and no explicit variant yet
- **THEN** the workspace continues to expose a usable image
- **AND** migration can backfill an equivalent primary variant without changing the canonical asset id
