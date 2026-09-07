## ADDED Requirements

### Requirement: Source classification SHALL distinguish audible content from structural text
The system SHALL classify current-episode source lines using structural precedence, explicit voice markers, and trusted current-script character names and aliases. A colon alone MUST NOT classify a line as dialogue, and every classified segment SHALL retain its stable source ID, original text, offsets, and coverage status.

#### Scenario: Cast metadata contains a colon
- **WHEN** a source line is `出场人物：Cassian、Serena`
- **THEN** the system classifies the line as structural metadata or action rather than audible dialogue
- **AND** the line is not included in the required sound set

#### Scenario: Camera direction contains a colon
- **WHEN** a source line describes a camera direction and contains a colon within the description
- **THEN** the system classifies the line as visual action rather than dialogue

#### Scenario: Known character speaks
- **WHEN** a source line uses a current-script character name or explicit alias as its speech prefix
- **THEN** the system classifies the line as `DIALOGUE`

#### Scenario: Explicit voice marker is present
- **WHEN** a source line contains an explicit VO, VS, OS, narration, or off-screen-voice marker
- **THEN** the system classifies the line as `NARRATION` or `INNER_OS` according to the marker

#### Scenario: Unknown colon prefix is ambiguous
- **WHEN** a colon-prefixed line is neither structural text nor an explicit or known-speaker voice line
- **THEN** the system retains it as required visual coverage without adding it to the required sound set
- **AND** the system records a low-confidence classification warning

### Requirement: Schema v3 SHALL derive sound ownership from trusted source order
New storyboard Agent generations SHALL use Schema v3. The model SHALL provide ordered storyboard boundaries and MAY provide an ordered `sourceAnchor` for each internal shot, but MUST NOT be required to enumerate `soundSegmentIds`. The backend SHALL derive every final sound ID and trusted sound-text field from the current episode source.

#### Scenario: Anchored shots contain multiple sounds
- **WHEN** a storyboard range contains dialogue, narration, or inner-OS segments and its shots provide valid non-decreasing source anchors
- **THEN** the system assigns each audible segment exactly once to the first applicable anchored shot
- **AND** the persisted shot plan contains the derived sound IDs and unmodified source text

#### Scenario: Shot anchors are omitted
- **WHEN** one or more internal shots omit `sourceAnchor`
- **THEN** the system derives monotonic anchors from trusted source order, shot order, and shot duration weights
- **AND** it completes sound ownership without invoking the model again

#### Scenario: Non-audible segments are present
- **WHEN** a storyboard range contains action, metadata, scene, and audible segments
- **THEN** only `DIALOGUE`, `NARRATION`, and `INNER_OS` segments appear in final sound ownership

#### Scenario: Sound remains after the final explicit anchor
- **WHEN** audible source segments occur after every explicit shot anchor in a storyboard
- **THEN** the system assigns the remaining audible segments to the final shot in source order

### Requirement: Mechanical storyboard fields SHALL be canonicalized deterministically
The system SHALL treat array order and trusted source order as authoritative for mechanical bookkeeping. It SHALL write consecutive storyboard and internal-shot numbers, derive each storyboard start from the prior canonical end, and extend the final storyboard through the final required episode segment without changing model-authored creative content.

#### Scenario: Submitted numbering has gaps
- **WHEN** an otherwise valid payload contains non-consecutive storyboard or shot numbers
- **THEN** the system writes canonical consecutive numbers based on array order
- **AND** it does not request a model correction

#### Scenario: Adjacent storyboard starts disagree
- **WHEN** a later storyboard start does not equal the required segment after the prior storyboard end
- **THEN** the system derives the later start from the prior canonical end
- **AND** the persisted coverage contains no gap or overlap

#### Scenario: Final coverage endpoint is omitted
- **WHEN** the final storyboard has a valid ordered range but does not reach the final required source segment
- **THEN** the system extends the final canonical range through the final required segment

#### Scenario: Creative endpoint is impossible
- **WHEN** a model-authored storyboard endpoint is unknown, reversed, or outside the current episode
- **THEN** the system rejects the payload with a structured hard failure
- **AND** it does not silently select a different dramatic boundary

### Requirement: Subjective action quality SHALL NOT block persistence
The system SHALL treat action density, sequence words, combined emotion changes, and combined camera/performance prose as quality signals rather than data-integrity failures. These signals MUST NOT cause a save rejection or an implicit model call when technical duration and structure constraints pass.

#### Scenario: Dialogue contains the word Then
- **WHEN** a shot contains `Then` inside quoted dialogue
- **THEN** the system does not reject the shot as containing multiple actions

#### Scenario: Action contains a sequence word
- **WHEN** a shot action contains `随后`, `然后`, `接着`, `继而`, `并且`, or an equivalent sequence word
- **THEN** the system MAY record an action-density warning
- **AND** the warning does not change save success or trigger a model call

#### Scenario: Action is technically valid but visually dense
- **WHEN** a shot satisfies Schema, source, material, and duration invariants but appears to contain several creative beats
- **THEN** the system saves the shot and exposes a quality warning with its storyboard and shot position

### Requirement: Hard validation SHALL protect trusted scope and downstream invariants
The system SHALL reject stale, unauthorized, malformed, or technically unusable storyboard data. Hard validation SHALL include trusted scope, episode fingerprint, required structure, known source and material references, ordered creative endpoints, atomic replacement, and supported duration limits.

#### Scenario: Episode changes during generation
- **WHEN** the submitted episode fingerprint differs from the fingerprint captured by the current Run
- **THEN** the system rejects the save and preserves the prior active storyboard set

#### Scenario: Reference belongs outside trusted scope
- **WHEN** a payload references a source segment or material outside the current trusted script and episode scope
- **THEN** the system rejects the entire atomic replacement

#### Scenario: Duration is unsupported
- **WHEN** a storyboard or internal-shot duration is outside the configured downstream technical range
- **THEN** the system rejects the payload with an actionable duration diagnostic

### Requirement: Storyboard business failures SHALL NOT trigger implicit model correction
The storyboard Workflow Agent SHALL perform at most one business model generation per Run. A failed `save_episode_storyboards` business validation SHALL end the Run with a structured diagnostic and MUST NOT be returned to the model for a correction round. Provider transport retries SHALL remain independently bounded and observable.

#### Scenario: Storyboard save fails hard validation
- **WHEN** the first storyboard save attempt fails a hard business validation
- **THEN** the Run ends as failed and preserves the prior active storyboard set
- **AND** no second business model invocation is made

#### Scenario: Provider transport fails recoverably
- **WHEN** the provider invocation encounters a configured recoverable connection, throttling, or timeout failure
- **THEN** the invocation layer applies its existing bounded technical retry policy
- **AND** diagnostics distinguish the technical retry from business correction

#### Scenario: User wants a new generation after failure
- **WHEN** a user explicitly retries or regenerates a failed episode
- **THEN** the system creates a new execution according to the existing lifecycle and billing rules

### Requirement: New generation SHALL preserve downstream and historical compatibility
The system SHALL continue to read and edit historical Schema v2 storyboards. Schema v3 generation SHALL persist the final sound IDs and trusted sound-text fields required by existing downstream dubbing, lip-sync, subtitle, prompt, and video workflows.

#### Scenario: Existing Schema v2 storyboard is opened
- **WHEN** a user opens or edits a storyboard created before this change
- **THEN** the existing storyboard remains readable without backfill

#### Scenario: Downstream workflow reads a Schema v3 generation
- **WHEN** dubbing, lip-sync, subtitle, prompt, or video generation reads a newly generated storyboard
- **THEN** it receives the same final sound ownership and trusted text fields it already consumes

#### Scenario: Episode is regenerated
- **WHEN** an episode with historical storyboards is successfully regenerated under Schema v3
- **THEN** the existing atomic replacement and historical media preservation rules remain in effect

### Requirement: Workbench SHALL distinguish quality warnings from failures
The system SHALL expose classification, normalization, derived-sound, action-quality, hard-failure, business-call, and technical-retry diagnostics. The workbench SHALL display successful storyboard generation with warnings separately from failed generation and MUST NOT present a warning as requiring paid retry.

#### Scenario: Generation succeeds with action warning
- **WHEN** a generated episode is saved with one or more action-density warnings
- **THEN** the workbench shows the episode as successful with warning details
- **AND** no retry action is implied by the warning

#### Scenario: Batch generation contains mixed results
- **WHEN** a batch contains successes, successes with warnings, and hard failures
- **THEN** the batch summary reports each category separately
- **AND** it reports business model calls separately from provider technical retries
