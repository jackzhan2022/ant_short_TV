## ADDED Requirements

### Requirement: Raw AI recognition output is retained before normalization
The system SHALL retain the raw provider response and its execution, attempt, call-log, task, stage, and script-version linkage before using recognition data to prepare asset candidates.

#### Scenario: Provider returns structurally invalid asset data
- **WHEN** an AI call succeeds at the transport level but its asset data fails normalization
- **THEN** the raw response remains available to authorized diagnostics
- **AND** no malformed value is written into canonical asset columns

### Requirement: Recognition shapes are normalized into typed candidates
The system SHALL convert documented compatible response shapes into typed character, scene, and prop candidates and SHALL validate every candidate's required fields before persistence.

#### Scenario: Provider returns string arrays
- **WHEN** `characters`, `scenes`, or `props` contains a non-blank string
- **THEN** the normalizer converts it into a candidate whose `name` is that string
- **AND** supplies only documented safe defaults for optional fields

#### Scenario: Candidate name is missing
- **WHEN** a candidate has no non-blank normalized name
- **THEN** the system records a candidate-level validation diagnostic
- **AND** prevents the candidate from reaching an asset table with a null name

#### Scenario: Provider returns supported alternate top-level fields
- **WHEN** a response uses a documented compatible wrapper or alias such as `assets`, `short_drama_assets`, `locations`, or `key_items`
- **THEN** the normalizer converts it to the canonical three-array contract before validation

### Requirement: Duplicate and alias candidates produce reviewable merge proposals
The system SHALL normalize candidate names and aliases within the same tenant, project, and asset type and SHALL record deterministic grouping and proposed canonical targets for user review.

#### Scenario: Deterministic aliases identify the same asset
- **WHEN** multiple candidates have the same normalized name or an explicitly recorded alias relationship
- **THEN** the system groups them into one review proposal
- **AND** preserves source values and match evidence

#### Scenario: Match is ambiguous
- **WHEN** fuzzy similarity does not establish a deterministic identity
- **THEN** the system keeps the candidates separate
- **AND** requires a user decision before merging them into a confirmed asset

### Requirement: Normalization promotion is transactional and idempotent
The system SHALL promote accepted candidates and merge decisions atomically and SHALL prevent repeated execution attempts from creating duplicate canonical assets, variants, or decisions.

#### Scenario: User accepts a new canonical asset
- **WHEN** an authorized user accepts a valid unmatched candidate
- **THEN** the system creates one canonical asset and any approved initial variant in one transaction
- **AND** records the promotion decision

#### Scenario: Promotion is retried
- **WHEN** the same normalization run and candidate decision is submitted again
- **THEN** the system returns the existing outcome without creating duplicate rows

#### Scenario: Promotion fails midway
- **WHEN** any canonical asset, variant, alias, or binding write fails during promotion
- **THEN** the transaction rolls back all writes for that decision
- **AND** the candidate remains reviewable with a normalized error
