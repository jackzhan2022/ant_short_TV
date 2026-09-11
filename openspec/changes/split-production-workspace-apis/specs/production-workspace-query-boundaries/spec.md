## ADDED Requirements

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
The system SHALL provide the visual variants, generation summary, episode bindings, review state, and resolved media for one authorized asset when its visual editor or gallery is opened.

#### Scenario: Open one asset gallery
- **WHEN** the user opens the gallery for a character, scene, or prop
- **THEN** the frontend requests only that asset's visual workspace
- **AND** failures are shown within the gallery without discarding the settings-page summaries

### Requirement: Storyboards load by episode and page
The system SHALL provide episode navigation metadata and a bounded page of storyboards for the requested episode rather than embedding every project storyboard in a shared workspace response.

#### Scenario: Open the storyboard page
- **WHEN** an authorized user opens the storyboard page
- **THEN** the frontend requests the first bounded storyboard page for the selected episode
- **AND** the response includes total and pagination metadata

#### Scenario: Switch episode or page
- **WHEN** the user selects a different episode or pagination position
- **THEN** the frontend requests only that episode and page
- **AND** a stale earlier response does not replace the current selection

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
