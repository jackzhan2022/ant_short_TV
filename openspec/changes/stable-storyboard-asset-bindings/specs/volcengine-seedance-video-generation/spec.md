## MODIFIED Requirements

### Requirement: Seedance request input maps from the existing video task contract
The Seedance adapter SHALL construct its request solely from the resolved Model and persisted video-task snapshot. The snapshot SHALL contain prompt and generation options plus the eligible structured storyboard and manual Mention references selected during task preparation. The adapter SHALL map the selected image, video, and audio references to the provider request in deterministic order and SHALL record any storyboard bindings omitted because of model constraints.

#### Scenario: Text-to-video task is submitted
- **WHEN** a video task has no eligible media reference
- **THEN** the Ark request contains the text prompt and configured generation options without a media content item

#### Scenario: One image reference is selected
- **WHEN** task preparation selects one eligible image reference
- **THEN** the Ark request contains the prompt and that image content item
- **AND** the task snapshot identifies its storyboard asset and variant when applicable

#### Scenario: Multiple multimodal references are selected
- **WHEN** the resolved Seedance model accepts several image, video, or audio references
- **THEN** the Ark request contains the deterministic provider-supported subset in role and storyboard order
- **AND** duplicate media identities appear only once

#### Scenario: Storyboard references exceed the model limit
- **WHEN** eligible storyboard bindings and manual Mentions exceed a configured media limit
- **THEN** task preparation retains the highest-ranked supported references without deleting storyboard bindings
- **AND** the task exposes each omitted reference and omission reason
