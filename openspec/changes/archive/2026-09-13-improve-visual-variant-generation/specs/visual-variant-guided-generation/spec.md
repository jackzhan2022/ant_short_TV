## ADDED Requirements

### Requirement: Derive a persistent visual-variant generation prompt
The system SHALL derive and persist a visual variant's generation prompt when the variant prompt is empty. The derived content MUST include the canonical asset prompt or a type-specific fallback, the variant name, and the variant appearance when present. The system MUST NOT overwrite a non-empty variant prompt.

#### Scenario: AI-recognized character look has no prompt
- **WHEN** a character visual variant with an empty prompt is read or prepared for generation
- **THEN** the system persists a prompt composed from the character asset prompt, look name, and look appearance
- **AND** the prompt instructs the image model to retain the character's identity

#### Scenario: User-edited variant prompt exists
- **WHEN** a visual variant has a non-empty prompt saved by a user
- **THEN** the system returns and uses that prompt unchanged

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

