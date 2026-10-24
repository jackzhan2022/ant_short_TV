## ADDED Requirements

### Requirement: Reviewable asynchronous simplification
The system SHALL analyze project props through the existing billed AI task system and save a complete, evidence-backed plan without applying production changes.

#### Scenario: Analysis finishes
- **WHEN** an authorized user submits prop simplification
- **THEN** the task saves a reviewable plan and the original assets remain unchanged until explicit application

### Requirement: Production policies preserve identities and versions
The system SHALL support independent production, shared appearance, distinct visual versions and scene inclusion without deleting original props, images or usage records.

#### Scenario: Owners share appearance
- **WHEN** props belonging to different characters share one production item
- **THEN** the original identities remain available and image production uses the shared item

#### Scenario: Important versions
- **WHEN** a plan retains different phone screens or cooking stages
- **THEN** those versions remain separately selectable and an unresolved required version does not silently use a generic main image

### Requirement: Atomic application and undo
The system SHALL validate tenant scope, script and asset fingerprints, version ownership, and active production tasks before applying an edited plan atomically; it SHALL allow undoing the latest applied production policies.

#### Scenario: Stale plan
- **WHEN** the script or frozen inventory changes after analysis
- **THEN** application is rejected without changing production policies

#### Scenario: Undo
- **WHEN** an authorized user undoes the latest applied plan
- **THEN** previous production policies are restored and generated media is retained

### Requirement: Downstream production integration
The system SHALL group the prop list by production item, deduplicate image batches, and resolve storyboard and video references using the approved production policies.

#### Scenario: Repeated tools
- **WHEN** several selected props use the same shared appearance
- **THEN** image generation submits that production item once

#### Scenario: Scene furniture
- **WHEN** furniture is assigned to a scene
- **THEN** the furniture is included in scene generation context and shown as requiring scene confirmation rather than a missing standalone prop image

### Requirement: Existing workbench interaction
The system SHALL expose simplification next to asset extraction, show grouped recommendations in a review dialog, and retain existing asset cards and visual variant controls after application.

#### Scenario: Review and apply
- **WHEN** the user changes a group's handling mode and applies selected recommendations
- **THEN** the list refreshes to production items and original names can be inspected in the existing visual workspace
