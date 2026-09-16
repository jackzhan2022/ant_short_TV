# visual-variant-guided-generation Specification

## Purpose
TBD - created by archiving change improve-visual-variant-generation. Update Purpose after archive.
## Requirements
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

### Requirement: Generate role costume variants from the canonical reference image
The system SHALL require a usable primary image for the canonical character before creating an image task for a non-primary character visual variant. The task MUST include the canonical primary image in `referenceImages` and the visual variant prompt as its prompt.

#### Scenario: Character primary image is usable
- **WHEN** a user generates an image for a character visual variant and the character has a completed primary image
- **THEN** the system creates the image task with that primary image as a reference
- **AND** the task uses the visual variant's persisted prompt

#### Scenario: Character primary image is missing
- **WHEN** a user generates an image for a non-primary character visual variant and no usable character primary image exists
- **THEN** the system does not create an image task
- **AND** reports that the user must generate the character primary image first

### Requirement: Keep non-character visual generation independent
The system SHALL generate scene and prop visual variants from their persisted variant prompts without requiring a canonical reference image.

#### Scenario: Scene default visual has no image
- **WHEN** a user generates a scene visual variant with no canonical scene image
- **THEN** the system creates a text-to-image task using the scene variant prompt
