## MODIFIED Requirements

### Requirement: Storyboards load by episode and page
The system SHALL provide episode navigation metadata and a bounded page of storyboards for the requested episode rather than embedding every project storyboard in a shared workspace response. Each returned storyboard SHALL include its ordered structured asset-reference summary loaded through bounded batch queries without per-storyboard or per-reference query expansion.

#### Scenario: Open the storyboard page
- **WHEN** an authorized user opens the storyboard page
- **THEN** the frontend requests the first bounded storyboard page for the selected episode
- **AND** the response includes total, pagination, and ordered binding metadata
- **AND** binding projection does not execute one query per storyboard or reference

#### Scenario: Switch episode or page
- **WHEN** the user selects a different episode or pagination position
- **THEN** the frontend requests only that episode and page
- **AND** a stale earlier response does not replace the current selection

#### Scenario: Update one storyboard's materials
- **WHEN** the user replaces material bindings in one storyboard card
- **THEN** the frontend uses the focused material endpoint and its response to update that card
- **AND** does not reload the episode storyboard page
