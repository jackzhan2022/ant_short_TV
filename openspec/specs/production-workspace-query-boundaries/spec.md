# production-workspace-query-boundaries Specification

## Purpose
TBD - created by archiving change split-production-workspace-apis. Update Purpose after archive.
## Requirements
### Requirement: Script page loads a focused workspace
The system SHALL provide a script-page workspace response containing the current script, episodes, episode warnings, analysis state, global-understanding summary, and script-version metadata without unrelated assets, storyboards, or historical version bodies.

#### Scenario: Open a script page for a large project
- **WHEN** an authorized user opens the script page
- **THEN** the frontend requests the focused script-page workspace
- **AND** the response does not contain character, scene, prop, or storyboard collections
- **AND** historical versions do not contain full script content

### Requirement: Script version content loads on demand
The system SHALL return the full content of one script version only through an authorized version-detail request.

#### Scenario: Open a historical version
- **WHEN** an authorized user selects a script version
- **THEN** the frontend requests that version's detail
- **AND** the backend verifies that the version belongs to the requested tenant, project, and script before returning its content

### Requirement: Asset settings loads summary data without per-asset visual expansion
The system SHALL provide character, scene, and prop summaries required by the settings page without loading nested visual variants, episode bindings, candidate counts, or episode-aware resolved media for every asset.

#### Scenario: Open settings with many assets
- **WHEN** an authorized user opens the settings page
- **THEN** the frontend requests the asset-settings summary
- **AND** the backend returns persisted summary fields without executing per-asset visual-workspace expansion

### Requirement: Asset visual workspace loads for one asset on demand
The system SHALL provide the visual variants, generation summary, episode bindings, review state, and resolved media for one authorized asset when its visual editor or gallery is opened. Historical media candidates SHALL be loaded through bounded pages only after their selector or gallery is opened. Current selected variant and image identities SHALL remain available independently from candidate pagination.

#### Scenario: Open one asset gallery
- **WHEN** the user opens the gallery for a character, scene, or prop
- **THEN** the frontend requests only that asset's visual workspace and the first bounded candidate page required by the opened gallery
- **AND** failures are shown within the gallery without discarding the settings-page summaries

#### Scenario: Asset editor remains closed
- **WHEN** the user has not opened an asset's visual editor or candidate selector
- **THEN** the settings and storyboard pages do not fetch that asset's historical media candidates

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

### Requirement: Focused read contracts preserve authorization isolation
Every focused workspace and detail endpoint SHALL enforce active tenant membership, project access, and the same applicable project permission as the data it replaces.

#### Scenario: Request a resource outside the active scope
- **WHEN** a user requests a version, asset visual workspace, or storyboard page outside their tenant or project scope
- **THEN** the backend rejects the request without exposing resource existence or data

### Requirement: Legacy aggregate remains compatible during migration
The system SHALL retain `GET /api/projects/{projectId}/script-workspace` with its existing response shape while known production-workbench consumers migrate to focused endpoints.

#### Scenario: Legacy client requests the aggregate
- **WHEN** an authorized legacy client requests `script-workspace`
- **THEN** the backend returns the existing aggregate contract
- **AND** the focused endpoint implementation does not remove or rename its fields

### Requirement: Focused workspaces use bounded internal episode reads
The system SHALL load episode navigation for focused script and storyboard workspaces through a projection that excludes persisted episode body content. The full episode body SHALL remain available only through an authorized on-demand detail contract and existing write/agent workflows.

#### Scenario: Open script or storyboard workspace for a project with many episodes
- **WHEN** an authorized user loads a focused script or storyboard workspace
- **THEN** the backend reads and returns only the selected navigation fields for all episodes
- **AND** it does not read or serialize every episode body
- **AND** the requested storyboard page remains bounded by `pageSize`

### Requirement: Focused reads reuse verified access context
The system SHALL resolve active membership and project access once for each focused workspace or detail read, and SHALL reuse that verified context only within the current request.

#### Scenario: Read a focused workspace
- **WHEN** an authorized user requests a focused workspace
- **THEN** membership and project authorization are enforced before data loading
- **AND** internal helper calls do not repeat the same authorization lookup
- **AND** no authorization result is cached across requests

### Requirement: Focused read performance evidence is sanitized
The system SHALL collect request-level HTTP duration, SQL query count, and cumulative SQL duration for focused production-workspace reads using a normalized route label.

#### Scenario: A focused workspace request completes
- **WHEN** a selected focused GET request completes or fails
- **THEN** the performance record contains only normalized route, status, HTTP duration, SQL count, and cumulative SQL duration
- **AND** it does not contain project IDs, request headers, credentials, SQL text, parameters, script content, or response bodies

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
