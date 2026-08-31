## ADDED Requirements

### Requirement: Provide an independently runnable per-episode asset Agent
The system SHALL provide an enabled workflow Agent identified by `short-drama-asset-recognition` that recognizes formal assets from one trusted current active episode per Run.

#### Scenario: Run recognition for one current episode
- **WHEN** a recognition child Run starts
- **THEN** it reads the episode content and compact current-script asset catalog through trusted scope
- **AND** does not depend on global understanding, episode summary, or historical script-version results

### Requirement: Load recognition Skills and only the required tools
The Agent SHALL load `short-drama-analysis-foundation` followed by `short-drama-asset-recognition-framework`, expose only `read_current_episode` and `save_episode_assets`, and require those calls in that order.

#### Scenario: Preserve recognition configuration
- **WHEN** the Agent Run is created
- **THEN** it snapshots both Skills, the model configuration, and exactly the read and asset-save tools

### Requirement: Recognize five formal asset categories
The Agent SHALL recognize characters, character looks, scenes, props, and states of the same prop, and SHALL NOT produce relationships between distinct props.

#### Scenario: Character changes clothing
- **WHEN** the episode evidences a production-visible change in clothing, hair, makeup, or accessories
- **THEN** the Agent returns a character look owned by the corresponding character

#### Scenario: Prop changes visible state
- **WHEN** the same logical prop becomes visibly damaged, stained, burned, opened, disassembled, or recombined
- **THEN** the Agent returns a prop-state variant owned by that prop

#### Scenario: A different object is produced from a prop
- **WHEN** the episode creates a separately usable object from an existing prop
- **THEN** the Agent does not output a derived-prop relationship

### Requirement: Reuse trusted asset keys and stable semantic identity
The read tool SHALL provide opaque existing asset and variant keys with stable names and explicit aliases, and the Skill SHALL require reuse of a supplied key whenever the episode refers to the same logical entity.

#### Scenario: Episode uses an established nickname
- **WHEN** the current name is an explicit alias of an existing character
- **THEN** the Agent returns that character's supplied asset key
- **AND** does not create a second character

#### Scenario: Similar names do not prove identity
- **WHEN** two names are merely similar and the episode cannot establish they are the same entity
- **THEN** the Agent does not guess an existing key

### Requirement: Apply deterministic server-side matching
The save tool SHALL resolve validated supplied keys first, then exact normalized canonical names, then exact explicit aliases; it MUST NOT automatically merge fuzzy or ambiguous matches.

#### Scenario: New output has one exact alias match
- **WHEN** a run-local entity name resolves to exactly one existing explicit alias in the same tenant, project, script, and asset type
- **THEN** the tool reuses that stable asset ID

#### Scenario: A name resolves to multiple candidates
- **WHEN** a name or alias resolves to multiple eligible formal assets
- **THEN** the tool rejects the item with `ENTITY_MATCH_AMBIGUOUS` and safe candidate keys
- **AND** commits no partial payload from that tool call

#### Scenario: Concurrent calls attempt the same new name
- **WHEN** concurrent episode Runs attempt to create the same script-scoped normalized asset
- **THEN** row locking and active uniqueness cause both calls to resolve to one formal identity

### Requirement: Scope variants to their logical owner
Character looks SHALL match only within one character, and prop states SHALL match only within one prop; scene time or atmosphere SHALL remain episode usage metadata rather than a new scene identity.

#### Scenario: Two characters use a look with the same name
- **WHEN** two different characters both have a look named `职业装`
- **THEN** the system stores distinct variants under their respective character assets

#### Scenario: One location appears by day and night
- **WHEN** an episode uses the same physical scene at a different time or atmosphere
- **THEN** the system reuses the scene identity
- **AND** records time or atmosphere in episode usage metadata

### Requirement: Save formal assets and episode bindings transactionally
`save_episode_assets` SHALL validate evidence against the episode snapshot, upsert identities and variants, and replace Agent-managed bindings for that episode in one transaction.

#### Scenario: Valid episode assets are saved
- **WHEN** the complete payload passes schema, ownership, evidence, and matching validation
- **THEN** formal assets and variants are immediately queryable and editable
- **AND** their active episode bindings refer to the trusted `episode_id`

#### Scenario: Any item is invalid
- **WHEN** one item lacks a required owner, name, evidence, or valid key
- **THEN** the complete tool call rolls back
- **AND** the Run can retry with corrected output

### Requirement: Finalize replacement only after complete script coverage
The recognition stage SHALL retire obsolete AI-managed bindings, variants, and unreferenced assets only after every episode in the frozen stage snapshot has a successful current recognition result.

#### Scenario: Some episode Runs fail
- **WHEN** recognition succeeds for some but not all snapshot episodes
- **THEN** completed episode data remains available
- **AND** the system does not execute whole-script retirement

#### Scenario: All episode Runs succeed
- **WHEN** every snapshot episode has committed recognition data
- **THEN** the finalizer retires no-longer-observed AI-managed data
- **AND** preserves matched stable IDs, generated or uploaded media, and unrelated user-created assets

