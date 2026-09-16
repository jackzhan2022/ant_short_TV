## MODIFIED Requirements

### Requirement: Fill the visual generation prompt field with the variant prompt
The visual-image management interface SHALL render the selected visual's effective persisted prompt in the visual generation input. A primary/default visual SHALL expose and edit the owning canonical asset prompt, while a non-primary visual SHALL expose and edit its own variant prompt. The system MUST NOT derive a missing prompt during workspace reads.

#### Scenario: User opens a primary/default visual
- **WHEN** a user selects a primary/default character, scene, or prop visual
- **THEN** the interface displays the canonical asset prompt
- **AND** does not display a separately stored or derived primary variant prompt

#### Scenario: User opens a non-primary visual
- **WHEN** a user selects a non-primary visual with a persisted variant prompt
- **THEN** the interface displays that variant prompt unchanged

#### Scenario: Selected visual has no applicable prompt
- **WHEN** the selected primary canonical asset or non-primary variant has an empty prompt
- **THEN** the interface leaves the prompt input empty
- **AND** image generation reports that the applicable asset or variant prompt must be completed

#### Scenario: User edits a visual generation prompt
- **WHEN** a user changes and saves the selected visual's prompt
- **THEN** the system updates the canonical asset for a primary visual or the variant for a non-primary visual
- **AND** later reads return that same effective value without derivation
