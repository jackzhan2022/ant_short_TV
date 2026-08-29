## MODIFIED Requirements

### Requirement: Extracted drafts replace previous unconfirmed drafts
The system SHALL replace unconfirmed normalized candidates for the requested element type with the latest successful normalization run while preserving confirmed canonical assets and their visual variants. Provider response values SHALL NOT be inserted directly into canonical asset tables.

#### Scenario: Re-extracting one element type
- **WHEN** AI extraction and normalization succeed for characters, scenes, or props
- **THEN** the system retires unconfirmed candidates from superseded runs of that same element type in the tenant and project
- **AND** inserts or updates the latest normalized candidates for review
- **AND** keeps confirmed canonical assets and their variants unchanged until user confirmation

#### Scenario: Extracting all element types
- **WHEN** AI extraction is requested for all element types
- **THEN** the system applies candidate replacement independently for characters, scenes, and props

#### Scenario: One candidate is invalid
- **WHEN** normalization identifies a missing required name or unsupported field type
- **THEN** the system records an actionable validation error before canonical persistence
- **AND** does not rely on a database non-null constraint as the validation boundary

### Requirement: Name-based merge targets are prepared during extraction
The system SHALL prepare reviewable merge proposals when a normalized candidate deterministically matches an existing confirmed asset of the same tenant, project, and element type by normalized name or explicit alias. Ambiguous fuzzy matches SHALL NOT select a merge target automatically.

#### Scenario: Extracted element matches a confirmed element deterministically
- **WHEN** a normalized candidate has the same normalized name or explicit alias as a confirmed asset in the same tenant, project, and element type
- **THEN** the system prepares a pending merge proposal linked to that confirmed asset
- **AND** records the matching evidence

#### Scenario: Extracted element does not match a confirmed element
- **WHEN** a normalized candidate has no deterministic confirmed-asset match in the same tenant, project, and element type
- **THEN** the system prepares it as a standalone review candidate
- **AND** leaves its merge target empty

#### Scenario: Fuzzy match is ambiguous
- **WHEN** a candidate is merely similar to multiple confirmed assets
- **THEN** the system does not automatically link it to any target
- **AND** presents the alternatives for explicit review

#### Scenario: Merge target lookup is isolated
- **WHEN** another tenant, project, or element type has a confirmed asset with the same normalized name or alias
- **THEN** the system MUST NOT link the candidate to that unrelated confirmed asset
