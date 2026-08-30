## ADDED Requirements

### Requirement: Direct screenplay follows the Markdown shooting-script format
The system SHALL instruct the video-understanding model to place a Markdown-formatted Chinese shooting screenplay in the protocol `script` string and SHALL structurally validate the required markers before accepting the result.

#### Scenario: Accept the required screenplay structure
- **WHEN** `script` starts with `# 第{episodeNo}集：{title}`, contains one or more consecutive `## {episodeNo}-{sceneNo} {time} {interiorOrExterior} {location}` scene headings, declares `出场人物：` for each scene, contains screenplay body text, and ends with `——本集完`
- **THEN** the system accepts the structure as a valid direct screenplay

#### Scenario: Represent dialogue and voice cues
- **WHEN** a scene contains spoken dialogue, off-screen speech, or voice-over
- **THEN** each speaking character cue appears on its own line, an optional full-width parenthetical appears on the next line, and dialogue follows without quotation marks
- **AND** off-screen and voice-over cues use `（OS）` and `（VO）` respectively

#### Scenario: Reject an incomplete required structure
- **WHEN** the protocol object contains a non-empty `script` but the episode heading, a valid scene heading, a scene cast declaration, or the final `——本集完` marker is missing
- **THEN** the system treats the provider response as a retryable business parsing failure and does not save a screenplay result

### Requirement: Screenplay content remains evidence-bound
The system SHALL require the direct screenplay to preserve the video's event order and only describe visible or audible evidence. It SHALL express an existing ending suspense through the final depicted action or dialogue without adding a `结尾钩子：` label or inventing a hook absent from the video.

#### Scenario: Video ends on a suspenseful event
- **WHEN** the source video ends with an action, dialogue, or revelation that creates suspense
- **THEN** the screenplay ends with that depicted event followed by `——本集完`
- **AND** does not add an explanatory hook label

#### Scenario: Video has no ending hook
- **WHEN** the source video ends without a suspenseful event
- **THEN** the screenplay faithfully records the actual ending and does not invent additional action or dialogue

## MODIFIED Requirements

### Requirement: Video understanding produces a directly reviewable script
The system SHALL require new video-understanding responses to be a complete JSON object containing a non-empty `script` string. The `script` value SHALL contain one complete episode screenplay in the configured Markdown shooting-script format and SHALL be accepted only after protocol, truncation, and structural validation.

#### Scenario: Video model returns a valid direct screenplay
- **WHEN** a video-understanding provider returns a complete protocol object whose `script` satisfies the configured episode and scene structure
- **THEN** the system accepts the response as a successful video decomposition result

#### Scenario: Video model omits or truncates the screenplay
- **WHEN** the response omits a non-empty `script`, cannot be parsed as complete JSON, or reports token-limit truncation
- **THEN** the system marks the attempt as a retryable business parsing failure and retains the provider response for diagnostics

## REMOVED Requirements

### Requirement: Direct screenplay becomes the editable draft
**Reason**: Successful direct screenplays are now immutable independent decomposition results; manual editing, review, and project import are removed from the new workflow.

**Migration**: Historical `draft_content`, draft versions, review statuses, and confirmed script-version links remain readable but are not used for newly created batches.
