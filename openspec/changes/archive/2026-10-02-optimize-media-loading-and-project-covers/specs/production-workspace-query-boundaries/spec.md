## MODIFIED Requirements

### Requirement: Storyboards load by episode and page
The system SHALL provide episode navigation metadata and a bounded page of storyboards for the requested episode rather than embedding every project storyboard in a shared workspace response. The frontend SHALL load accompanying media summaries only for that page's storyboard identities and authorized associated assets rather than requesting all project image, video, and voice tasks. Current selected and bound media SHALL be resolved independently from historical candidate pagination.

#### Scenario: Open the storyboard page
- **WHEN** an authorized user opens the storyboard page
- **THEN** the frontend requests the first bounded storyboard page for the selected episode
- **AND** the response includes total and pagination metadata
- **AND** accompanying media queries exclude unrelated project task histories

#### Scenario: Switch episode or page
- **WHEN** the user selects a different episode or pagination position
- **THEN** the frontend requests only that episode, page, and associated media summaries
- **AND** a stale earlier response does not replace the current selection

#### Scenario: Current video belongs to an old task
- **WHEN** a displayed storyboard is bound to a result outside the first history page
- **THEN** its summary still includes the authorized current result and compressed cover
- **AND** a more recent task does not replace the binding

### Requirement: Asset visual workspace loads for one asset on demand
The system SHALL provide the visual variants, generation summary, episode bindings, review state, and resolved media for one authorized asset when its visual editor or gallery is opened. Historical media candidates SHALL be loaded through bounded pages only after their selector or gallery is opened. Current selected variant and image identities SHALL remain available independently from candidate pagination.

#### Scenario: Open one asset gallery
- **WHEN** the user opens the gallery for a character, scene, or prop
- **THEN** the frontend requests only that asset's visual workspace and the first bounded candidate page required by the opened gallery
- **AND** failures are shown within the gallery without discarding the settings-page summaries

#### Scenario: Asset editor remains closed
- **WHEN** the user has not opened an asset's visual editor or candidate selector
- **THEN** the settings and storyboard pages do not fetch that asset's historical media candidates

## ADDED Requirements

### Requirement: Page media summaries use verified batch identities
The system SHALL accept at most 100 storyboard identities in a project-scoped media-summary read, validate their tenant and project ownership before resolving associated media, and return bounded latest, selected, and active-task summary data. It SHALL batch bound result and associated asset reads and reuse the verified access context within the request. Client-supplied object paths or cross-project result identities MUST NOT replace validated domain bindings.

#### Scenario: Read a current page's media
- **WHEN** an authorized user requests media summaries for the current storyboard page
- **THEN** only the requested storyboards and their validated associations are resolved through bounded batch queries
- **AND** no per-storyboard complete task-history query occurs

#### Scenario: Batch contains a foreign storyboard
- **WHEN** the requested storyboard identities include one outside the authorized tenant or project
- **THEN** the request is rejected without returning media or signed URLs for that foreign identity

#### Scenario: One generation finishes
- **WHEN** a visible or newly submitted media task reaches its terminal state
- **THEN** the frontend refreshes only the affected current media summary and opened candidate page
- **AND** it does not reload all project media task histories
