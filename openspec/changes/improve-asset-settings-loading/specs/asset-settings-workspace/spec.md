## ADDED Requirements

### Requirement: Asset settings workspace returns only settings data
The system SHALL expose `GET /api/projects/{projectId}/asset-settings-workspace` for authorized project members. The response SHALL contain the project identifier and the project's character, scene, and prop assets, including each asset's existing visual variants, generation summary, resolved visual image, and episode bindings. The response SHALL NOT contain script content, script versions, episodes, storyboards, script-analysis state, or global understanding.

#### Scenario: Authorized member reads the asset settings workspace
- **WHEN** an authorized member requests the asset settings workspace for a project with formal assets and visual variants
- **THEN** the response contains the project's character, scene, and prop assets with their visual data
- **AND** the response omits all non-asset script workspace fields

#### Scenario: Unauthorized member reads the asset settings workspace
- **WHEN** a request lacks membership or project-view authorization
- **THEN** the system rejects the request using the existing authorization behavior

### Requirement: Asset settings workspace isolates projects
The system SHALL scope asset settings workspace data to the requested project and tenant.

#### Scenario: Project has no formal assets
- **WHEN** an authorized member requests the asset settings workspace for a project with no assets
- **THEN** the response returns empty character, scene, and prop collections for that project
