## ADDED Requirements

### Requirement: Provide bounded episode asset candidates
The current-episode tool SHALL return complete source coverage data independently of a bounded asset candidate summary. Candidates SHALL prioritize current episode bindings and exact source name or explicit alias matches and expose opaque keys, names and prompt-presence metadata. Partial catalogs SHALL explicitly expose total, hasMore and continuation information and SHALL NOT imply that omitted assets do not exist.

#### Scenario: Script exceeds the old catalog limit
- **WHEN** a script contains at least 215 props and any asset has more than 50 variants
- **THEN** episode loading succeeds without expanding all assets or variants and provides continuation information

#### Scenario: Candidate budget is exhausted
- **WHEN** candidate count or serialized byte budget is reached
- **THEN** the response marks the catalog incomplete while preserving episode source segments and required coverage

### Requirement: Search global identities and page variant details
The system SHALL expose search_script_assets and read_asset_details within trusted script and selected asset scope. Search SHALL support exact normalized names and explicit aliases with stable keyset pagination; detail reads SHALL page variants and expose hasPrompt without automatically loading complete prompt text. Asset pages SHALL contain at most 50 entries, detail requests at most 10 keys, variant pages at most 20 entries, and responses SHALL respect a documented serialized byte budget.

#### Scenario: Matching asset was omitted from initial candidates
- **WHEN** an Agent searches an established alias absent from the initial page
- **THEN** it can retrieve the existing asset key and reuse it without creating a duplicate

#### Scenario: More variants exist than fit one response
- **WHEN** an asset has 51 variants
- **THEN** all authorized variants remain reachable through continuation pages without failing solely on total count

#### Scenario: Foreign key or cursor is supplied
- **WHEN** keys or cursors belong to another tenant, script, query or forbidden asset type
- **THEN** the tool rejects them without returning that data

### Requirement: Keep global matching authoritative on save
The save tool SHALL perform exact server-side matching against the complete authorized identity space even when the Agent observed only a candidate subset. Catalog paging or stale observations SHALL NOT bypass identity locks, uniqueness, ambiguity checks or prompt policy.

#### Scenario: Another child creates an asset after lookup
- **WHEN** a child submits a new identity that was created after its directory lookup
- **THEN** save resolves it to the existing exact identity and does not insert a duplicate
