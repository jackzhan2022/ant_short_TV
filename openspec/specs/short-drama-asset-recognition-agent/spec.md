# short-drama-asset-recognition-agent Specification

## Purpose
TBD - created by archiving change add-remaining-short-drama-analysis-agents. Update Purpose after archive.
## Requirements
### Requirement: Provide an independently runnable per-episode asset Agent
The system SHALL provide an enabled workflow Agent identified by `short-drama-asset-recognition` that recognizes formal assets from one trusted current active episode per Run.

#### Scenario: Run recognition for one current episode
- **WHEN** a recognition child Run starts
- **THEN** it reads the episode content and compact current-script asset catalog through trusted scope
- **AND** does not depend on global understanding, episode summary, or historical script-version results

### Requirement: Load recognition Skills and only the required tools
The Agent SHALL load short-drama-analysis-foundation followed by short-drama-asset-recognition-framework and expose only read_current_episode, search_script_assets, read_asset_details and save_episode_assets. It SHALL read the current episode first, optionally search or load authorized details, and finish with formal asset saving. The Skill SHALL require lookup before proposing an identity absent from the candidate subset and reuse trusted matching keys.

#### Scenario: Preserve recognition configuration
- **WHEN** the Agent Run is created
- **THEN** it snapshots both Skills, the model configuration, and exactly the episode-read, catalog-search, detail-read and asset-save tools

#### Scenario: Search is needed before saving
- **WHEN** a recognized entity has no trustworthy key in the initial candidate subset
- **THEN** the Agent searches the scoped global catalog before proposing a new identity and can load matching details before saving

#### Scenario: Initial candidates suffice
- **WHEN** all recognized entities match trusted initial candidates
- **THEN** the Agent can save immediately after reading the episode without mandatory extra lookup calls

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
The read tool SHALL provide opaque existing asset and variant keys with stable names, explicit aliases, and a `hasPrompt` indicator for each canonical asset and visual variant. The Skill SHALL require reuse of a supplied key whenever the episode refers to the same logical entity and SHALL use `hasPrompt` to avoid reproducing established prompts.

#### Scenario: Episode uses an established nickname
- **WHEN** the current name is an explicit alias of an existing character
- **THEN** the Agent returns that character's supplied asset key
- **AND** does not create a second character

#### Scenario: Similar names do not prove identity
- **WHEN** two names are merely similar and the episode cannot establish they are the same entity
- **THEN** the Agent does not guess an existing key

#### Scenario: Existing prompt status is exposed
- **WHEN** the Agent reads the compact asset catalog
- **THEN** each canonical asset and visual variant states whether a non-empty prompt already exists
- **AND** the catalog does not need to expose the complete prompt text

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
`save_episode_assets` SHALL validate evidence against the episode snapshot, upsert identities and variants, and replace Agent-managed bindings for that episode in one transaction. When a tool payload fails a correctable argument or schema validation, the Run SHALL allow at most one model correction using the returned validation details and MUST NOT synthesize evidence server-side.

#### Scenario: Valid episode assets are saved
- **WHEN** the complete payload passes schema, ownership, evidence, and matching validation
- **THEN** formal assets and variants are immediately queryable and editable
- **AND** their active episode bindings refer to the trusted `episode_id`

#### Scenario: Any item is invalid
- **WHEN** one item lacks a required owner, name, evidence, or valid key
- **THEN** the complete tool call rolls back
- **AND** the Run can retry with corrected output

#### Scenario: First save payload has a correctable schema error
- **WHEN** the first `save_episode_assets` call omits a required field such as scene `evidence`
- **THEN** the tool commits no partial payload
- **AND** the Run returns the precise validation error to the model
- **AND** allows one corrected call grounded in the trusted episode text

#### Scenario: Corrected save payload is still invalid
- **WHEN** the correction opportunity has been used and the next `save_episode_assets` payload remains invalid
- **THEN** the Run terminates with the final validation error
- **AND** performs no third save attempt or unbounded model retry

#### Scenario: Episode text contains no supporting evidence
- **WHEN** the model cannot locate source text supporting a proposed asset
- **THEN** the Run omits that unsupported asset or fails validation
- **AND** the server does not invent or substitute evidence

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

### Requirement: Produce prompts within episode asset recognition
The per-episode asset Agent SHALL produce type-specific `prompt` values in the same `save_episode_assets` payload for newly created canonical assets and visual variants, and for matched records that the trusted catalog reports as missing a prompt. Prompt content MUST follow `short-drama-asset-recognition-framework`, while identity and alias evidence MUST continue to satisfy the verbatim current-episode evidence contract.

#### Scenario: Catalog reports a missing canonical prompt
- **WHEN** `read_current_episode` returns an existing asset with `hasPrompt=false` and the episode identifies that asset
- **THEN** the Agent submits a prompt for that asset together with its stable asset key

#### Scenario: Catalog reports an established canonical prompt
- **WHEN** `read_current_episode` returns an existing asset with `hasPrompt=true`
- **THEN** the Agent reuses the stable asset key without reproducing or rewriting the prompt

#### Scenario: Prompt fields are not available in the tool contract
- **WHEN** a deployed `save_episode_assets` Schema does not declare `prompt`
- **THEN** the Agent omits unsupported prompt fields
- **AND** still saves the evidence-backed asset payload using the deployed contract

### Requirement: Provide sufficient output capacity for asset prompts
The asset-recognition Agent SHALL have an output budget of at least 16384 tokens and MUST preserve atomic failure semantics when a model response is truncated or omits required asset arrays.

#### Scenario: Recognition output is truncated
- **WHEN** the model response ends before the complete five-category payload can be validated
- **THEN** no partial asset, prompt, visual variant, or episode binding is committed

### Requirement: Scope formal recognition to selected asset types
The per-episode asset-recognition Agent and `save_episode_assets` tool SHALL accept `ALL`, `CHARACTER`, `SCENE`, or `PROP` scope. A non-`ALL` Run MUST read, emit, validate, and persist only its selected canonical asset type and its owned visual variants.

#### Scenario: Character-only Agent Run
- **WHEN** a child Agent Run starts with `CHARACTER` scope
- **THEN** it recognizes characters and character looks only
- **AND** the formal write tool rejects scene or prop payload collections

#### Scenario: All-type Agent Run
- **WHEN** a child Agent Run starts with `ALL` scope
- **THEN** it retains the existing five-category recognition behavior

### Requirement: Finalize only the selected recognition scope
The recognition finalizer SHALL retire or replace Agent-managed bindings, variants, and unreferenced assets only for the operation scope and only after every episode in the frozen scoped snapshot succeeds.

#### Scenario: Scene-only run succeeds
- **WHEN** all child Runs for a `SCENE` operation succeed
- **THEN** the finalizer may retire stale AI-managed scene bindings, scene variants, and unreferenced scenes for that snapshot
- **AND** it does not retire character, character-look, prop, or prop-state data

#### Scenario: Scoped run has a failed episode
- **WHEN** any child Run in a scoped operation fails
- **THEN** successful scoped episode writes remain available
- **AND** the finalizer does not retire prior data for that scope

