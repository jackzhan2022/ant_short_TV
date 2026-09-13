## MODIFIED Requirements

### Requirement: Script intake and inspiration browsing
The system SHALL let the user paste script text, import a supported local script file, or import the content of a selected version from an accessible independent review script on the first step, and SHALL show inspiration items that can be browsed independently of whether script content is present.

#### Scenario: User pastes script content
- **WHEN** the user pastes script text into the first step
- **THEN** the system keeps the script content available for later creation steps and related inspiration browsing

#### Scenario: User imports a local text or Markdown file
- **WHEN** the user selects a non-empty `.txt` or `.md` file on the first step
- **THEN** the browser reads the file content without uploading the file
- **THEN** the system uses the resulting text as the editable current script draft

#### Scenario: User imports a local Word file
- **WHEN** the user selects a non-empty `.docx` file on the first step
- **THEN** the system sends the file to a stateless server-side parser
- **THEN** the parser returns extracted text without saving the file or creating review data
- **THEN** the system uses the returned text as the editable current script draft

#### Scenario: User selects an independent review script version
- **WHEN** the user opens the review-script source and expands an accessible review project
- **THEN** the system displays every version returned for that review project as a distinct selectable option
- **WHEN** the user confirms one version
- **THEN** the system uses that version's complete content as the editable current script draft
- **THEN** the system does not persist a binding between the creation project and the review project or version

#### Scenario: Imported content would replace an existing draft
- **WHEN** the script input already contains non-blank content and the user attempts another import
- **THEN** the system asks the user to confirm replacement before changing the draft
- **THEN** canceling the confirmation preserves the existing draft and its source indicator

#### Scenario: Import fails or produces no text
- **WHEN** local reading, server parsing, project loading, or version loading fails or produces empty content
- **THEN** the system reports the failure
- **THEN** the system preserves the existing script draft

#### Scenario: User edits imported content
- **WHEN** content has been imported from a file or review version
- **THEN** the user can edit it as ordinary script text before continuing
- **THEN** project creation submits only the final edited `initialScriptContent` and no source identifier

#### Scenario: User selects an unsupported file type
- **WHEN** the user attempts to import a file other than `.txt`, `.md`, or `.docx`
- **THEN** the system rejects the file without changing the current draft

#### Scenario: User has no script content
- **WHEN** the script input is empty
- **THEN** the system still shows a usable inspiration gallery with default curated items

#### Scenario: User switches inspiration category
- **WHEN** the user switches between inspiration categories
- **THEN** the gallery updates to show the selected category's items without losing the current script draft
