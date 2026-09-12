# asset-prompt-lifecycle Specification

## Purpose
TBD - created by archiving change generate-asset-prompts-during-recognition. Update Purpose after archive.
## Requirements
### Requirement: Generate type-specific canonical asset prompts
The system SHALL generate a production prompt for every newly recognized character, scene, and prop, and for any matched canonical asset whose prompt is empty. Character prompts MUST use the configured upper-body close-up and full-body three-view Markdown structure, scene prompts MUST use the configured four-view grid and static-scene exclusions, and prop prompts MUST describe only the prop body, structure, material, color, surface treatment, and fixed details.

#### Scenario: New character is recognized
- **WHEN** the asset Agent recognizes a character that does not match an existing canonical asset
- **THEN** it submits a non-empty Markdown character prompt using the character template
- **AND** the prompt separates the fixed image task from the character-description fields

#### Scenario: New scene is recognized
- **WHEN** the asset Agent recognizes a new physical location
- **THEN** it submits a non-empty scene prompt containing the four-view grid instructions
- **AND** the prompt prohibits people, animals, unrelated props, dynamic effects, combat effects, and output text

#### Scenario: New prop is recognized
- **WHEN** the asset Agent recognizes a new prop
- **THEN** it submits a non-empty prompt describing only the prop itself
- **AND** does not add characters, actions, scenes, camera instructions, or generic composition suffixes

### Requirement: Keep evidence and visual completion separate
Asset evidence MUST remain a verbatim current-episode reference. A generated prompt MAY complete production-visible details that are absent from the episode only when they do not conflict with current evidence or trusted asset metadata, and MUST NOT present those completions as evidence or change core geometry, scene topology, explicit materials, ownership, or plot function.

#### Scenario: Production details are absent
- **WHEN** a character is evidenced but face shape, hair, body proportion, or accessory details are not specified
- **THEN** the Agent may create consistent visual-production details in the prompt
- **AND** leaves the verbatim evidence unchanged

#### Scenario: Identity-sensitive detail is unknown
- **WHEN** nationality, ethnicity, gender, or age has no reliable source
- **THEN** the corresponding prompt field states that it is unspecified
- **AND** the Agent does not infer it from a name, language, occupation, or stereotype

### Requirement: Store prompts without overwriting established content
The system SHALL store generated prompts in the existing canonical asset and visual-variant prompt columns within the same transaction that saves episode assets. It MUST write prompts for new records and fill empty prompts on matched records, and MUST NOT replace an existing non-empty prompt.

#### Scenario: New asset payload is valid
- **WHEN** a new asset and its required prompt pass schema, evidence, identity, and ownership validation
- **THEN** the asset and prompt are committed atomically

#### Scenario: Matched asset has an empty prompt
- **WHEN** the Agent matches an existing asset reported with `hasPrompt=false` and supplies a valid prompt
- **THEN** the system fills that empty prompt in the same transaction

#### Scenario: Matched asset already has a prompt
- **WHEN** the Agent matches an existing asset reported with `hasPrompt=true`
- **THEN** the existing prompt remains unchanged
- **AND** the Agent is not required to reproduce the prompt

### Requirement: Preserve visual-variant prompts as deltas
Character-look and prop-state prompts SHALL contain only the production-visible delta from the canonical asset. A character-look prompt MAY retain a reliably known minimal identity anchor required by the image model, while it MUST NOT repeat the canonical appearance or add generic composition, style, or consistency instructions.

#### Scenario: Character changes into a wet white dress
- **WHEN** the episode establishes a female character look with a wet white dress and bare feet
- **THEN** the saved variant prompt may be exactly `性别:女；衣着描述:白色连衣裙，裙摆湿透，赤脚`

#### Scenario: Episode contains only one visible look
- **WHEN** a newly recognized character has one visible look and no different canonical look exists
- **THEN** the look is used as the canonical character clothing description
- **AND** the system does not create a duplicate character-look variant with the same content

### Requirement: Use persisted prompts as the only image-task prompt source
The system SHALL select the image-task prompt from persisted asset data and MUST NOT derive it from an asset name or append variant names, canonical prompts, or generic type suffixes at task creation time.

#### Scenario: Canonical visual is generated
- **WHEN** a primary character, scene, or prop visual is submitted for generation
- **THEN** the image task uses the canonical asset prompt unchanged

#### Scenario: Character costume is generated
- **WHEN** a non-primary character look with a non-empty prompt and usable canonical image is submitted
- **THEN** the image task uses the look prompt unchanged
- **AND** includes the canonical character image as its only reference image

#### Scenario: Required persisted prompt is missing
- **WHEN** an image task target has no applicable persisted prompt
- **THEN** the system rejects task creation with an actionable validation error
- **AND** does not fall back to the asset or variant name

### Requirement: Backfill only missing historical prompts
The existing prompt-generation operation SHALL process only assets and variants whose prompts are empty, SHALL use the same type-specific prompt contract, and MUST consume and persist the model result instead of replacing prompts with hard-coded SQL strings.

#### Scenario: Historical asset prompt is empty
- **WHEN** an authorized user requests prompt generation for a target type containing empty prompts
- **THEN** the operation generates and stores prompts only for those empty records

#### Scenario: All target prompts already exist
- **WHEN** an authorized user requests prompt generation and all selected records have non-empty prompts
- **THEN** the operation completes without changing any prompt

### Requirement: Present persisted prompts consistently
The asset settings interface SHALL display and edit the persisted canonical or variant prompt and MUST NOT maintain a separate client-side prompt derivation algorithm.

#### Scenario: User opens visual generation
- **WHEN** a user opens generation for an asset or visual variant
- **THEN** the prompt field displays the persisted prompt that the server will use
- **AND** submitting without edits does not transform that prompt

