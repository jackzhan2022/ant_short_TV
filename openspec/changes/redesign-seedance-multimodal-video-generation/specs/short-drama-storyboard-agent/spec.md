## ADDED Requirements

### Requirement: Storyboard prompts use version 2 typed media Mentions
The system SHALL store newly created or edited storyboard prompt documents as version 2 documents. A version 2 Mention SHALL identify its media type, stable project-owned source, optional visual variant, and display name without treating a browser-supplied URL as authoritative.

#### Scenario: User inserts an image material
- **WHEN** a user selects a project character, scene, prop, visual variant, or storyboard image in the prompt material selector
- **THEN** the editor inserts a version 2 image Mention that retains the human-readable material name and stable source identity

#### Scenario: User inserts video or audio material
- **WHEN** a user selects a project-owned video or audio material
- **THEN** the editor inserts a version 2 video or audio Mention with stable source identity and the existing Mention visual treatment

#### Scenario: Prompt document is saved
- **WHEN** the user saves an edited version 2 prompt document
- **THEN** the system persists its text and typed Mention order as the authoritative input for subsequent video-task compilation

### Requirement: Version 1 prompt documents are removed without conversion
The system MUST clear stored version 1 storyboard prompt-document JSON during migration, MUST retain the corresponding plain `video_prompt`, and MUST NOT provide a runtime version 1 compatibility or automatic conversion path.

#### Scenario: Migration finds a version 1 prompt document
- **WHEN** the cleanup migration encounters a storyboard whose prompt document has version 1
- **THEN** it clears `prompt_document_json` and preserves `video_prompt` unchanged

#### Scenario: Storyboard with cleared prompt needs multimodal generation
- **WHEN** a user opens a storyboard whose version 1 document was cleared
- **THEN** the user must bind materials again or regenerate the storyboard before creating a version 2 multimodal video task

#### Scenario: Client submits a version 1 prompt document after deployment
- **WHEN** a client attempts to save a version 1 prompt document
- **THEN** the system rejects it with a validation error and does not recreate legacy data
