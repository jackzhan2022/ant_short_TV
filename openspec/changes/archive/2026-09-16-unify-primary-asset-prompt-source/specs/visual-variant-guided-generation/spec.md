## MODIFIED Requirements

### Requirement: Derive a persistent visual-variant generation prompt
The system SHALL treat a prompt on `asset_visual_variant` as an independent generation prompt only for a non-primary variant. It MUST NOT derive or persist prompts for primary variants, and it MUST NOT synthesize a missing non-primary prompt by concatenating canonical prompts, variant names, appearances, or consistency suffixes.

#### Scenario: Primary visual is read or prepared for generation
- **WHEN** a primary/default character, scene, or prop visual is read or prepared
- **THEN** the system returns the owning canonical asset prompt as the effective prompt
- **AND** does not read, derive, or update the primary variant prompt value

#### Scenario: Non-primary variant has a persisted prompt
- **WHEN** a non-primary visual variant has a non-empty prompt saved by AI or a user
- **THEN** the system returns and uses that prompt unchanged

#### Scenario: Historical non-primary variant has no prompt
- **WHEN** a non-primary visual variant has an empty prompt
- **THEN** the system reports the prompt as missing
- **AND** does not concatenate the canonical prompt, variant name, appearance, or a generic consistency instruction

#### Scenario: Historical primary variant contains a duplicate prompt
- **WHEN** a primary variant retains a historical prompt value
- **THEN** the system ignores that value for display, editing, and generation
- **AND** retains it in storage for rollback compatibility
