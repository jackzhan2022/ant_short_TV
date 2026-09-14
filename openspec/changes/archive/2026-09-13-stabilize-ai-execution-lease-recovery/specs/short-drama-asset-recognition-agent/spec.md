## MODIFIED Requirements

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
