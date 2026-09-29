## MODIFIED Requirements

### Requirement: Submit a versioned structured storyboard set
`save_episode_storyboards` SHALL accept versioned structured JSON containing the trusted episode fingerprint and an ordered non-empty `storyboards` array. Every storyboard SHALL contain its episode-local creative plan, source-range intent, ordered character, scene, and prop references using stable asset keys, optional visual-variant keys and roles, and an ordered non-empty `shots` array. Every shot SHALL contain positioning, action, and references to source dialogue, narration, or inner-OS segment IDs. The server SHALL derive or normalize mechanical numbering, source anchors, compatible totals, and defaults before persistence.

#### Scenario: Model submits an ordered set with stable material keys
- **WHEN** the model supplies valid creative storyboard items and material keys
- **THEN** the save tool resolves the keys against the frozen asset catalog
- **AND** validates and normalizes the complete set before persistence
- **AND** preserves exact internal-shot decimal durations in structured storage

#### Scenario: Internal shot numbering is invalid
- **WHEN** submitted shot numbers do not restart from 1 or contain gaps
- **THEN** the server renumbers shots deterministically in their submitted order
- **AND** records the changed paths in normalization diagnostics
- **AND** does not fail the episode solely for numbering

### Requirement: Preserve complete plot order and exact spoken content
The server SHALL assign episode-local IDs from `S0001` upward to storyboard-relevant non-blank physical lines, retain their exact text and trusted offsets, and bind them to the episode fingerprint. The server SHALL normalize proposed source anchors and ranges against those trusted segments so the storyboard set covers every required segment in source order without omission or duplication. Dialogue, narration, and inner OS SHALL be injected from trusted segment text and each source utterance SHALL belong to exactly one internal shot.

#### Scenario: Complete valid coverage
- **WHEN** ordered source segment ranges cover every required segment and every utterance segment is assigned once
- **THEN** coverage validation succeeds without normalization warnings

#### Scenario: Source anchors are out of range or non-monotonic
- **WHEN** model-supplied anchors fall outside the storyboard source range or move backwards
- **THEN** the server clamps and reorders them from trusted offsets
- **AND** records repair diagnostics
- **AND** generation continues without another full-episode planning call

#### Scenario: Dialogue text is changed or repeated
- **WHEN** an utterance supplied by the model differs from its source text or is assigned repeatedly
- **THEN** the server replaces it with the trusted source text and assigns it once by source order
- **AND** records the normalization instead of persisting model-altered dialogue

### Requirement: Resolve only actually used materials deterministically
Each storyboard SHALL contain ordered character, scene, and prop reference objects for materials actually used by that storyboard. The save tool SHALL resolve stable asset and variant keys from the frozen catalog, choose an eligible visual variant when omitted, and persist resolved, image-pending, or unresolved binding state. Ambiguous or unknown legacy names MUST NOT be silently bound and MUST NOT fail the episode solely for material resolution.

#### Scenario: Episode-bound variant exists
- **WHEN** a used asset omits a variant and has a visual variant bound to the current episode
- **THEN** the corresponding binding uses that asset ID and episode-bound variant ID

#### Scenario: Asset is valid but its image is missing
- **WHEN** a used stable asset key resolves but no eligible variant has a usable image
- **THEN** the binding is persisted as `ASSET_PENDING`
- **AND** the storyboard remains formally generated and editable

#### Scenario: Material identity is ambiguous
- **WHEN** a legacy name or model source name does not resolve uniquely
- **THEN** the system persists an `UNRESOLVED` binding with the source name
- **AND** reports a material warning instead of failing the episode

### Requirement: Replace an episode storyboard set atomically
`save_episode_storyboards` SHALL parse, normalize, resolve, validate, correct, and deterministically complete the entire candidate set before persistence. It SHALL then lock and revalidate the trusted episode fingerprint and replace all active storyboards and their bindings in one transaction. Authorization, execution ownership, or stale-source failures SHALL roll back. Repairable content or material findings SHALL be normalized or persisted with diagnostics and SHALL NOT by themselves block publication.

#### Scenario: Valid regeneration succeeds
- **WHEN** the complete candidate set is valid against the unchanged episode
- **THEN** all old active storyboards for that episode are retired
- **AND** all new storyboards and bindings identify the current Agent Run as generator

#### Scenario: Repairable findings remain after correction
- **WHEN** one targeted correction call still leaves source-anchor, numbering, duration, optional-field, or material-resolution findings
- **THEN** deterministic fallback completes the candidate set
- **AND** the complete episode is published atomically with warnings

#### Scenario: Source changes before publish
- **WHEN** the episode fingerprint changes after trusted reads
- **THEN** the transaction rolls back
- **AND** every prior active storyboard and binding remains unchanged

### Requirement: Retry safely and report formal completion
The asynchronous execution SHALL retry provider transport failures at most three times. A correctable model or save response SHALL receive at most one focused correction call containing only invalid items and exact error paths. Repeated repairable validation codes SHALL trigger deterministic fallback rather than fail the attempt. A Run SHALL succeed only when the complete current episode storyboard and binding set was committed, and SHALL expose normalization, correction, fallback, unresolved-material, and image-pending diagnostics.

#### Scenario: Provider transport repeatedly fails
- **WHEN** every allowed provider transport attempt fails before a parseable candidate exists
- **THEN** the execution is marked failed with provider diagnostics
- **AND** the prior formal storyboard set remains available

#### Scenario: Correction repeats a repairable error
- **WHEN** the focused correction output repeats an anchor, numbering, duration, optional-field, or material-resolution error
- **THEN** the server applies deterministic fallback
- **AND** commits the complete episode with warning diagnostics

#### Scenario: Fatal validation fails
- **WHEN** authorization, execution ownership, or trusted episode fingerprint validation fails
- **THEN** the execution is marked failed without deterministic fallback
- **AND** the prior formal storyboard and binding set remains available

## ADDED Requirements

### Requirement: Preserve manually locked materials during regeneration
The storyboard generation service SHALL carry manually locked material bindings to a regenerated storyboard with the same deterministic trusted source identity unless the authorized request explicitly enables material overwrite.

#### Scenario: Matching regenerated storyboard has manual locks
- **WHEN** a replacement storyboard matches the prior trusted source range and material overwrite is false
- **THEN** the new storyboard retains the locked manual bindings and order
- **AND** AI bindings fill only unlocked material positions
