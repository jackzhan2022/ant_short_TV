## ADDED Requirements

### Requirement: Persist ordered storyboard asset bindings
The system SHALL persist each storyboard's character, scene, and prop references as ordered project-scoped records containing asset type, asset identity, optional visual-variant identity, reference role, resolution status, provenance, source name, and user-lock state. The system MUST verify that every resolved asset and variant belongs to the requested tenant, project, type, and owning asset.

#### Scenario: Save multiple resolved materials
- **WHEN** an authorized user saves several characters, scenes, and props with valid independent visual variants for one storyboard
- **THEN** the system persists every ordered binding transactionally
- **AND** a subsequent read returns the same assets, variants, roles, and order

#### Scenario: Reject a mismatched variant
- **WHEN** a request binds a variant that belongs to a different asset, project, tenant, or asset type
- **THEN** the system rejects the complete replacement without modifying existing bindings

#### Scenario: Preserve an unresolved source name
- **WHEN** an AI or legacy material name cannot be resolved uniquely
- **THEN** the system persists an `UNRESOLVED` reference with its source name and no asset or variant identity
- **AND** does not guess a binding silently

### Requirement: Replace one storyboard's bindings through a focused contract
The system SHALL provide an authorized focused endpoint that replaces one storyboard's complete ordered binding list in one transaction. The endpoint SHALL treat an empty list as an explicit clear, SHALL synchronize the legacy `characters`, `scene`, and `props` display fields, and SHALL return the updated storyboard binding projection.

#### Scenario: Replace bindings without reloading an episode
- **WHEN** a user adds, removes, reorders, or changes a variant in one storyboard card
- **THEN** the client sends one focused replacement request for that storyboard
- **AND** updates only that card from the response
- **AND** does not request the complete episode storyboard page

#### Scenario: Clear one material category
- **WHEN** the replacement contains no references for one asset type
- **THEN** all active references of that type are retired
- **AND** the corresponding legacy display field becomes empty
- **AND** references of other types remain as submitted

### Requirement: Resolve visual variants deterministically
For a resolved asset without an explicit valid variant, the system SHALL choose the current-episode preferred variant, then a usable primary variant, then the only usable variant, then a primary or only pending variant. A binding without a usable image SHALL remain persisted as `ASSET_PENDING` and MUST NOT fail storyboard generation or editing.

#### Scenario: Episode-preferred character look exists
- **WHEN** a character binding omits a variant and that character has an active preferred variant for the storyboard episode
- **THEN** the binding resolves to the episode-preferred variant

#### Scenario: Asset exists without a generated image
- **WHEN** an asset or selected variant is valid but has no usable image
- **THEN** the system persists the binding as `ASSET_PENDING`
- **AND** exposes an image-generation action without losing the binding

### Requirement: Preserve user-locked bindings across AI regeneration
Manual binding changes SHALL set `source_type = MANUAL` and `locked_by_user = true`. AI regeneration SHALL preserve locked bindings when the replacement storyboard has the same deterministic source identity unless the caller explicitly authorizes material overwrite.

#### Scenario: Regenerate an episode with manual material choices
- **WHEN** an existing storyboard has locked manual bindings and a regenerated storyboard matches its trusted source range
- **THEN** the new formal storyboard retains those bindings and their order
- **AND** AI-proposed replacements do not overwrite them

#### Scenario: Explicitly overwrite materials
- **WHEN** an authorized regeneration request explicitly enables material overwrite
- **THEN** AI material bindings may replace previously locked rows
- **AND** the replacement provenance is recorded

### Requirement: Read legacy material fields compatibly
For a storyboard without formal binding rows, the system SHALL split legacy material fields using supported delimiters and resolve exact normalized names or unique aliases. Ambiguous and missing values SHALL be returned as transient `LEGACY` unresolved references, and the first controlled save or backfill SHALL persist equivalent formal rows.

#### Scenario: Read exact legacy names
- **WHEN** every legacy name has one exact current-project asset match
- **THEN** the workspace returns ordered transient legacy references with resolved asset identities

#### Scenario: Read an ambiguous legacy name
- **WHEN** one legacy name matches multiple assets or aliases
- **THEN** the workspace still loads
- **AND** returns one unresolved legacy reference requiring confirmation

### Requirement: Provide a multi-reference storyboard editor
The storyboard workbench SHALL allow users to add, remove, reorder, and choose visual variants independently for multiple characters, scenes, and props. Character rows SHALL expose voice actions, scene references SHALL use image cards and roles, and all sections SHALL expose unresolved and image-pending state accessibly.

#### Scenario: Configure a multi-character storyboard
- **WHEN** a user adds three characters and chooses a different visual variant for each
- **THEN** all three rows remain visible in the selected order
- **AND** each row displays its own thumbnail, variant, status, and voice action

#### Scenario: Configure multiple scene references
- **WHEN** a user adds a main scene and supporting or transition scenes
- **THEN** the workbench shows separate image cards and roles
- **AND** saves every selected scene and variant

### Requirement: Compile authoritative bindings into media references
Prompt and video preparation SHALL use formal storyboard bindings as the authoritative material list, merge manual structured prompt Mentions, and deduplicate by media identity. Plain text resembling a material tag MUST NOT create a binding.

#### Scenario: Compile bindings and manual mentions
- **WHEN** a storyboard has resolved formal bindings and additional manual structured Mentions
- **THEN** the generated prompt and video task include all unique eligible references
- **AND** no asset-variant pair appears more than once

#### Scenario: Prompt contains angle-bracket text only
- **WHEN** a user types an asset display name in angle brackets as ordinary text
- **THEN** the text remains unchanged
- **AND** no asset identity or media reference is inferred
