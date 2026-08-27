# script-element-workflow-boundaries Specification

## Purpose
TBD - created by archiving change refactor-script-workflow-boundaries. Update Purpose after archive.
## Requirements
### Requirement: Element extraction delegates to focused workflow boundaries
The system SHALL keep the existing script element extraction API behavior while separating AI extraction, draft persistence, and confirmation responsibilities into focused backend components.

#### Scenario: Existing extraction endpoint remains compatible
- **WHEN** a user requests script element extraction through the existing project script API
- **THEN** the system returns the existing script workspace response shape without requiring frontend API changes

#### Scenario: Facade validates before delegation
- **WHEN** extraction is requested for a project script
- **THEN** the system validates tenant membership, project access, permissions, element type, and script content before invoking the element extraction component

### Requirement: Extracted drafts replace previous unconfirmed drafts
The system SHALL replace unconfirmed draft elements for the requested element type with the latest successful AI extraction result while preserving confirmed elements.

#### Scenario: Re-extracting one element type
- **WHEN** AI extraction succeeds for characters, scenes, or props
- **THEN** the system soft-deletes unconfirmed rows of that same element type in the tenant and project
- **AND** inserts the newly extracted rows for that element type
- **AND** keeps confirmed rows of that element type unchanged until user confirmation

#### Scenario: Extracting all element types
- **WHEN** AI extraction is requested for all element types
- **THEN** the system applies the draft replacement rule independently for characters, scenes, and props

### Requirement: Name-based merge targets are prepared during extraction
The system SHALL prepare merge review records when an extracted element name matches an existing confirmed element of the same tenant, project, and element type.

#### Scenario: Extracted element matches a confirmed element by name
- **WHEN** an extracted element has the same name as a confirmed element in the same tenant, project, and element type
- **THEN** the system inserts the extracted element as `PENDING_REVIEW`
- **AND** stores the confirmed element id in `merge_target_id`

#### Scenario: Extracted element does not match a confirmed element
- **WHEN** an extracted element has no confirmed element with the same name in the same tenant, project, and element type
- **THEN** the system inserts the extracted element as `DRAFT`
- **AND** leaves `merge_target_id` empty

#### Scenario: Merge target lookup is isolated
- **WHEN** another tenant, project, or element type has a confirmed element with the same name
- **THEN** the system MUST NOT link the new draft to that unrelated confirmed element

### Requirement: User confirmation applies merge updates
The system SHALL apply name-based merge updates only after the user explicitly confirms the pending element.

#### Scenario: Confirming pending review element
- **WHEN** a user confirms a `PENDING_REVIEW` element with a valid `merge_target_id`
- **THEN** the system updates the linked confirmed element with the pending element fields
- **AND** soft-deletes the pending element

#### Scenario: Confirming standalone draft element
- **WHEN** a user confirms a draft element without a merge target
- **THEN** the system marks that element `CONFIRMED`
- **AND** clears `merge_target_id`

### Requirement: Element workflow behavior is covered by focused tests
The system SHALL include backend tests that verify extraction persistence, draft replacement, merge target preparation, confirmation merge updates, and isolation by tenant, project, and element type.

#### Scenario: Regression tests protect extraction and confirmation rules
- **WHEN** backend tests are run for the script element workflow
- **THEN** they cover both AI extraction success with visible workspace data and user confirmation paths for all supported element types

