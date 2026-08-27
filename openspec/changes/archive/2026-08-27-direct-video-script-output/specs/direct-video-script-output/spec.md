## ADDED Requirements

### Requirement: Video understanding produces a directly reviewable script

The system SHALL require new video-understanding responses to be a JSON object containing a non-empty `script` string. The `script` value SHALL contain the complete episode screenplay in the configured professional format, including episode title, scene headers, audiovisual action, dialogue and subtitles when present, and an ending hook.

#### Scenario: Video model returns a valid direct screenplay

- **WHEN** a video-understanding provider returns `{"script":"第1集：[标题]..."}` with a non-empty script
- **THEN** the system accepts the response as a successful video analysis result

#### Scenario: Video model omits the screenplay

- **WHEN** a video-understanding provider returns a JSON object without a non-empty `script` string
- **THEN** the system marks the analysis as a retryable business parsing failure and retains the provider response for diagnostics

### Requirement: Direct screenplay becomes the editable draft

The system SHALL store a successful direct screenplay in the episode's `draft_content`, increment the draft version, and transition the episode to `PENDING_REVIEW` without invoking a text draft-generation model.

#### Scenario: New video analysis completes

- **WHEN** a pending video analysis succeeds with a valid direct screenplay
- **THEN** the episode exposes that screenplay as an editable pending-review draft and no `video_script_draft` provider call is created

#### Scenario: User edits and confirms a direct screenplay

- **WHEN** a user saves edits to, then confirms import of, a direct screenplay draft
- **THEN** the existing draft version checks and script-version import behavior apply unchanged

### Requirement: Historical draft-generation tasks remain executable

The system SHALL preserve the legacy text draft-generation path for episodes already in `PENDING_DRAFT` or `DRAFT_GENERATING` when the change is deployed.

#### Scenario: Historical episode resumes draft generation

- **WHEN** an episode created before this change is in `PENDING_DRAFT`
- **THEN** the system uses its stored normalized analysis JSON and existing text draft-generation workflow
