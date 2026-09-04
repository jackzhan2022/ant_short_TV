## ADDED Requirements

### Requirement: Provide an independently runnable per-episode storyboard Agent
The system SHALL provide an enabled workflow Agent identified by `short-drama-storyboard` that generates formal storyboards for one trusted current active episode per Run. The Agent SHALL be started explicitly from the storyboard page and SHALL NOT become a fifth automatic script-analysis stage.

#### Scenario: Generate one episode
- **WHEN** an authorized user selects an active episode and requests storyboard generation
- **THEN** the system creates one episode-scoped storyboard Agent Run
- **AND** the Run generates only that episode's complete storyboard set

#### Scenario: Reject partial-text generation
- **WHEN** a caller attempts to request generation for selected text or more than one episode
- **THEN** the system rejects the unsupported scope before starting the Agent Run

### Requirement: Use the project's frozen text-model configuration
Each storyboard Run SHALL resolve the project's configured TEXT model at submission and SHALL freeze the Agent revision, ordered Skill revisions, model configuration, episode identity, source fingerprint, tool allowlist, execution, and attempt before the first model call.

#### Scenario: Project configuration changes during a Run
- **WHEN** the project text model or an Agent Skill is changed while a storyboard Run is active
- **THEN** the active Run continues with its frozen configuration
- **AND** the next Run uses the newly published configuration

### Requirement: Load storyboard Skills with extensible Seedance guidance
The Agent SHALL load the common short-drama analysis foundation, storyboard planning, material-reference, and Seedance video-prompt Skills in a fixed order. The Seedance Skill SHALL treat its camera-motion and degree-adverb dictionary as examples and SHALL allow clear compatible natural-language extensions.

#### Scenario: Required expression is absent from the example dictionary
- **WHEN** a scene requires a clear camera or action expression not listed in the Seedance examples
- **THEN** the Agent may use an unambiguous natural-language expression
- **AND** does not fail solely because the phrase is absent from the example dictionary

### Requirement: Require trusted reads followed by a terminal save
The Agent SHALL expose and require `read_current_episode`, `read_adjacent_episodes`, `read_script_analysis`, `read_project_context`, `read_script_assets`, and `save_episode_storyboards` in that order. `save_episode_storyboards` SHALL be the only write tool and the terminal tool.

#### Scenario: Agent returns text without saving
- **WHEN** the model produces valid-looking storyboard content but does not successfully call `save_episode_storyboards`
- **THEN** the Run SHALL NOT succeed
- **AND** the content SHALL NOT become formal storyboard data

#### Scenario: Agent calls a tool out of order
- **WHEN** the Agent attempts to save before completing all required trusted reads
- **THEN** the tool contract rejects the call
- **AND** no storyboard data is changed

### Requirement: Read current trusted storyboard context
The read tools SHALL provide the current episode's full formal content and fingerprint, the previous episode's ending summary, the next episode's opening summary, current formal global understanding, project `visualStyle`, and current-script assets with aliases, visual variants, current-episode bindings, and project-primary selections. Other episodes' complete bodies and old storyboard content SHALL NOT be used as planning input.

#### Scenario: Generate a middle episode
- **WHEN** the Agent reads context for an episode with both neighbors
- **THEN** it receives the current episode body, previous ending summary, and next opening summary
- **AND** it does not receive old storyboard content as regeneration guidance

#### Scenario: Generate a boundary episode
- **WHEN** the current episode has no previous or next episode
- **THEN** the missing adjacent summary is returned as null
- **AND** generation remains allowed

### Requirement: Submit a versioned structured storyboard set
`save_episode_storyboards` SHALL accept versioned structured JSON containing the trusted episode fingerprint and an ordered non-empty `storyboards` array. Every storyboard SHALL contain its episode-local `storyboardNo`, source start and end markers, optional time and lighting, referenced asset keys, and an ordered non-empty `shots` array. Every shot SHALL contain a storyboard-local `shotNo`, decimal `durationSeconds`, positioning, action, and any source dialogue, narration, or inner OS.

#### Scenario: Model submits an ordered set
- **WHEN** all required fields satisfy the tool schema
- **THEN** the save tool validates the complete set before performing any persistence
- **AND** preserves exact internal-shot decimal durations in structured storage

#### Scenario: Internal shot numbering is invalid
- **WHEN** shot numbers do not restart from 1 and increase without gaps inside each storyboard
- **THEN** the complete save call fails
- **AND** no partial storyboard is persisted

### Requirement: Plan executable storyboard and shot durations
Each formal storyboard SHALL contain multiple chronologically ordered shots whose exact total duration is from 10 through 15 seconds. Each internal shot SHALL be from 1.5 through 4 seconds and SHALL carry one primary action or one explicit emotional change. A storyboard may cross spatially continuous adjacent locations, while time jumps, distant location changes, or clear dramatic section changes SHALL start a new storyboard.

#### Scenario: Storyboard totals 12.8 seconds
- **WHEN** the internal shot durations sum to 12.8 seconds
- **THEN** the structured plan retains 12.8 seconds
- **AND** the storyboard compatibility duration is stored as 13 seconds

#### Scenario: One shot contains multiple sequential events
- **WHEN** one proposed shot requires multiple primary actions that cannot execute within four seconds
- **THEN** the save validation rejects the plan for correction

### Requirement: Preserve complete plot order and exact spoken content
The storyboard set SHALL cover the current episode in source order without omission, duplication, or changed outcome. Dialogue, narration, and inner OS SHALL be copied verbatim, SHALL NOT be translated, polished, or invented, and each source utterance SHALL belong to exactly one internal shot.

#### Scenario: Complete valid coverage
- **WHEN** ordered source boundary markers cover the episode and every utterance appears once verbatim
- **THEN** coverage validation succeeds

#### Scenario: Dialogue is translated or repeated
- **WHEN** an utterance differs from its source text or is assigned to more than one shot
- **THEN** the complete save call fails
- **AND** existing formal storyboards remain unchanged

### Requirement: Allow bounded visual elaboration
The Agent MAY supplement lighting, composition, micro-expression, blocking, camera movement, and environmental detail needed for executable video prompting. It MUST NOT introduce new plot events, relationships, dialogue, key props, or outcomes. Project settings and bound materials SHALL take precedence over both source prose and Agent elaboration when visual descriptions conflict.

#### Scenario: Bound material conflicts with generated appearance text
- **WHEN** the selected character or scene material defines an appearance that conflicts with proposed elaboration
- **THEN** the material definition prevails
- **AND** the conflicting elaboration is not rendered into the formal prompt

### Requirement: Resolve only actually used materials deterministically
Each storyboard SHALL reference only the characters, scenes, and props actually used by that storyboard. The save tool SHALL resolve a supplied valid asset key and select its current-episode-bound visual variant first, then its project-primary variant, and otherwise leave the name unbound. Fuzzy or ambiguous names MUST NOT be silently bound.

#### Scenario: Episode-bound variant exists
- **WHEN** a used asset has a visual variant bound to the current episode
- **THEN** every corresponding material tag uses that asset ID and episode-bound variant ID

#### Scenario: No material can be matched
- **WHEN** a used name has no deterministic eligible material match
- **THEN** the prompt retains the ordinary name as text
- **AND** the storyboard is marked `ASSET_PENDING`

### Requirement: Render one complete editable prompt per storyboard
The system SHALL deterministically render one complete prompt document for every storyboard and SHALL expose it in one editable prompt input. The document SHALL contain the project visual style, the exact fixed sentence `视频中不得出现任何字幕、文字叠加、纯画面，不要bgm，不要配乐。`, material references, scene description, ordered shots, and the fixed consistency constraint. Empty material categories SHALL be omitted.

#### Scenario: Render a storyboard with matched materials
- **WHEN** a saved storyboard uses matched characters, scenes, and props
- **THEN** the prompt contains only those materials under `### 素材引用`
- **AND** contains `### 画面描写` with shots numbered from 1
- **AND** contains `### 约束词` with `保持<人物身份、数量、服装、道具归属、空间方向和声音关系>稳定。`

#### Scenario: Use a matched material throughout the prompt
- **WHEN** a matched asset appears in material references, scene setting, positioning, or action text
- **THEN** every occurrence is represented as a material Mention containing asset type, asset ID, variant ID, and display name

### Requirement: Treat the single prompt document as authoritative user input
The prompt editor SHALL present one editing surface per storyboard. After a user edits and saves it, that complete document SHALL be the authoritative prompt text for subsequent consumers. Re-entering a deleted material name as plain text SHALL NOT recreate its material binding automatically.

#### Scenario: User removes a material tag
- **WHEN** the user deletes a Mention and types the same display name as ordinary text
- **THEN** the saved prompt preserves ordinary text without asset identity metadata

### Requirement: Replace an episode storyboard set atomically
`save_episode_storyboards` SHALL lock and revalidate the trusted episode fingerprint, validate the entire submitted payload, then replace all active storyboards for that episode in one transaction, including draft, confirmed, locked, and manually edited records. It SHALL NOT read old storyboards as model context.

#### Scenario: Valid regeneration succeeds
- **WHEN** the complete new set passes validation against the unchanged episode
- **THEN** all old active storyboards for that episode are retired
- **AND** all new storyboards identify the current Agent Run as generator

#### Scenario: Source changes or validation fails
- **WHEN** the episode fingerprint changes after the read or any new storyboard is invalid
- **THEN** the transaction rolls back
- **AND** every old active storyboard remains unchanged

### Requirement: Preserve historical generated media without binding it to replacements
Replacing storyboards SHALL NOT physically delete historical first-frame, video-task, or video-result records. New storyboards SHALL receive new identities and SHALL NOT inherit old generated-media bindings.

#### Scenario: Replace storyboards that have generated results
- **WHEN** a successful Agent Run replaces an episode whose old storyboards have generated media
- **THEN** those media records remain available as history
- **AND** none is bound as the current result of a new storyboard

### Requirement: Retry safely and report formal completion
The asynchronous execution SHALL retry retryable Agent failures at most three times. A Run SHALL be successful only when the complete current episode storyboard set was committed by that Run; final failure SHALL preserve the prior set and expose a diagnostic error.

#### Scenario: All attempts fail
- **WHEN** schema, coverage, stale-source, or provider errors remain after the final allowed attempt
- **THEN** the execution is marked failed with an actionable error
- **AND** the prior formal storyboard set remains available

### Requirement: Remove fixed example storyboard generation
The `storyboard_breakdown` path SHALL use the new Workflow Agent result and SHALL NOT insert the fixed mansion, banquet-hall, or equity-agreement sample storyboards.

#### Scenario: Generate from unrelated episode content
- **WHEN** the current episode does not contain the former sample plot
- **THEN** no former sample storyboard is inserted
- **AND** every saved storyboard is traceable to the current episode and Agent Run
