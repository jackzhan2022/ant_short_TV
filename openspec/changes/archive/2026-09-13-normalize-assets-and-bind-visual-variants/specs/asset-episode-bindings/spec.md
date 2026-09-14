## ADDED Requirements

### Requirement: Visual variants bind to stable episodes
The system SHALL allow an authorized user to bind an active visual variant to one or more stable episodes belonging to the same tenant, project, and script.

#### Scenario: Bind a costume variant to episodes
- **WHEN** a user assigns a character costume variant to selected episodes
- **THEN** the system stores bindings to stable episode ids
- **AND** returns both episode ids and current episode numbers in the asset workspace

#### Scenario: Reject a cross-project binding
- **WHEN** a requested variant and episode belong to different tenants, projects, or scripts
- **THEN** the system rejects the binding without changing existing bindings

### Requirement: Each logical asset has at most one preferred variant per episode
The system SHALL enforce at most one active preferred visual variant for the same canonical asset and episode while permitting non-preferred alternatives to remain stored.

#### Scenario: Replace a preferred episode variant
- **WHEN** a user makes another active variant preferred for the same asset and episode
- **THEN** the system atomically removes the previous preferred marker
- **AND** keeps the previous variant available as a non-preferred alternative unless explicitly deleted

### Requirement: Downstream workflows resolve episode-aware visual references
The system SHALL resolve a logical asset's visual reference using the preferred episode binding first, then the asset's primary variant, then a supported legacy fallback during migration.

#### Scenario: Storyboard uses an episode costume
- **WHEN** a storyboard item in an episode references a character with a preferred bound costume variant
- **THEN** image and video preparation use that bound variant as the character reference

#### Scenario: No episode binding exists
- **WHEN** an asset has no usable preferred variant for the requested episode
- **THEN** the resolver uses the usable primary variant
- **AND** does not select an unrelated episode's preferred variant

### Requirement: Episode changes reconcile bindings conservatively
The system SHALL retain bindings when episode identity is confidently preserved and SHALL surface unresolved bindings when script edits make episode reconciliation ambiguous.

#### Scenario: Episode content changes without changing identity
- **WHEN** an ordinary script save retains an episode's explicit identity or stable content anchors
- **THEN** the episode keeps its stable id
- **AND** existing visual-variant bindings remain active

#### Scenario: Episode is removed or cannot be reconciled
- **WHEN** a new script version removes an episode or makes its identity ambiguous
- **THEN** the system does not silently attach its bindings to another episode number
- **AND** marks affected bindings as retired or requiring review
