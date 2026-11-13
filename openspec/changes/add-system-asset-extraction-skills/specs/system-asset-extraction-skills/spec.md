## ADDED Requirements

### Requirement: Select a system asset extraction skill per project
The system SHALL list system Skills marked for asset extraction and persist a validated project selection. Existing projects SHALL resolve to the legacy default. Exactly one selected asset strategy SHALL accompany fixed foundation rules.

#### Scenario: Select live action
- **WHEN** a user creates or edits a project with the live action skill
- **THEN** the response retains its code and subsequent extraction loads that strategy without the legacy art templates

#### Scenario: Invalid selection
- **WHEN** a submitted code is missing or is a different-purpose Skill
- **THEN** the system rejects it without silently selecting a default

### Requirement: Freeze effective extraction rules
All extraction entry points SHALL resolve trustworthy tenant and project context, snapshot the selected Skill content and revision, and reuse the same plan on retries. Estimation and duplicate fingerprints SHALL account for effective project rules.

#### Scenario: Edit after a batch starts
- **WHEN** a Skill or project selection changes during a batch
- **THEN** remaining episodes and retries use the batch snapshot

### Requirement: Preserve complete production prompts and facts
The complete prompt contract SHALL require independent nonempty positive and negative prompts and image ratios for newly generated asset and variant prompts, preserve structured visual identity and separate art supplementation from evidence. Scene states SHALL be variants of a stable identity. FILL_EMPTY SHALL preserve existing prompt packages.

#### Scenario: Save live action variants
- **WHEN** a character look, scene state or prop state is saved under the complete contract
- **THEN** its full prompt and generation specification are persisted with current episode evidence and a stable owner

#### Scenario: Preserve an existing prompt
- **WHEN** FILL_EMPTY reuses an asset with a nonempty prompt
- **THEN** its prompt and associated generation settings remain intact

### Requirement: Apply asset specifications to image generation
Single and batch image generation SHALL carry the saved negative prompt and ratio unless explicitly overridden. Complete strategy prompts SHALL avoid automatic incompatible art-style augmentation and SHALL retain reference images for identity continuity.

#### Scenario: Generate a live action prop
- **WHEN** the user generates a saved prop without overriding its settings
- **THEN** the image request uses its full positive prompt, negative prompt and 4:3 ratio

### Requirement: Protect selectable skills
The system SHALL protect built-in defaults and project-referenced asset Skills from deletion.

#### Scenario: Delete selected skill
- **WHEN** a platform editor attempts to delete a Skill selected by a project
- **THEN** deletion is rejected with an in-use error
