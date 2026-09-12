## ADDED Requirements

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

## MODIFIED Requirements

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

### Requirement: Save formal assets and episode bindings transactionally
`save_episode_assets` SHALL validate evidence against the episode snapshot, validate conditional prompt requirements, upsert identities and variants, fill only empty prompt fields, and replace Agent-managed bindings for that episode in one transaction. Existing non-empty canonical and variant prompts MUST remain unchanged.

#### Scenario: Valid episode assets are saved
- **WHEN** the complete payload passes schema, ownership, evidence, prompt, and matching validation
- **THEN** formal assets, prompts, and variants are immediately queryable and editable
- **AND** their active episode bindings refer to the trusted `episode_id`

#### Scenario: Any item is invalid
- **WHEN** one item lacks a required owner, name, evidence, prompt, or valid key
- **THEN** the complete tool call rolls back
- **AND** the Run can retry with corrected output

#### Scenario: Concurrent runs target the same empty prompt
- **WHEN** concurrent episode Runs resolve the same canonical asset or variant and both propose a prompt
- **THEN** row locking permits only the first empty-value fill
- **AND** the later Run preserves the now non-empty prompt
