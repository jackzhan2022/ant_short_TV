## MODIFIED Requirements

### Requirement: Script workspace exposes parsed episodes
The script workspace endpoint SHALL return an `episodes` collection derived from the current script content or the latest valid intelligent analysis for the current script version. Each episode SHALL include a stable project-scoped episode id, episode number, display title, and episode body content, while preserving all existing workspace fields.

#### Scenario: Parse numbered Chinese episode headings
- **WHEN** the script contains standalone headings such as `第1集` or `第12集：雨夜重逢`
- **THEN** the workspace returns one episode for each heading with a stable id and the corresponding number, title, and text until the next heading

#### Scenario: Use intelligent splitting for unstructured content
- **WHEN** the script has no reliable episode headings and a valid intelligent split result exists for the current version
- **THEN** the workspace returns the reconciled AI-generated episode identities, boundaries, and content
- **AND** preserves the analysis result for review

#### Scenario: Fall back while intelligent splitting is pending
- **WHEN** the script has no reliable episode headings and intelligent splitting is pending or failed
- **THEN** the workspace returns the deterministic one-episode fallback with a stable id
- **AND** exposes the intelligent splitting status separately

#### Scenario: Reconcile an ordinary script save
- **WHEN** a later script version retains an episode's explicit heading identity or stable content anchors
- **THEN** the episode retains its stable id even if its ordinal number or body text changes
- **AND** visual-variant episode bindings remain attached to that episode identity
