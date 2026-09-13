## ADDED Requirements

### Requirement: Expose canonical asset prompts in the settings workspace
The asset settings workspace SHALL return each canonical asset's generation prompt, and the settings page SHALL provide a compact way to view and edit it without requiring full script workspace data.

#### Scenario: User reviews an asset generation basis
- **WHEN** an authorized user opens an asset's visual-image management interface
- **THEN** the interface displays the canonical asset generation prompt
- **AND** the user can edit and save that prompt using existing asset editing authorization

### Requirement: Fill the visual generation prompt field with the variant prompt
The visual-image management interface SHALL render the selected visual variant's persisted prompt in its existing visual generation prompt input. An empty historical variant MUST receive the derived prompt before it is displayed or used for generation.

#### Scenario: User opens a historical empty-prompt variant
- **WHEN** a user selects a visual variant whose stored prompt is empty
- **THEN** the interface displays the system-derived visual generation prompt in the existing prompt input
- **AND** subsequent image generation uses that displayed prompt

#### Scenario: User edits a visual generation prompt
- **WHEN** a user changes the selected visual variant prompt and saves it
- **THEN** the interface persists the edited value for that variant
- **AND** later automatic derivation does not replace the edited value

