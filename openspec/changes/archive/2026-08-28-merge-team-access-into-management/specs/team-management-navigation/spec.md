## ADDED Requirements

### Requirement: Unified team management tabs
The team management page SHALL provide four tabs named Team List, Member Management, Role Management, and Permission Tree, with the existing team administration workflows available in their respective tabs.

#### Scenario: Open team management
- **WHEN** an authenticated user opens `/team/my`
- **THEN** the page displays the four tabs and shows the team list tab by default

#### Scenario: Switch management tabs
- **WHEN** a user selects Member Management, Role Management, or Permission Tree
- **THEN** the corresponding existing content is rendered without navigating to a separate top-level menu

### Requirement: Preserve team administration authorization
The unified page SHALL preserve the existing selected-team context and owner-only controls for inviting, removing members, changing roles, and mutating teams or roles.

#### Scenario: Non-owner views management
- **WHEN** a non-owner opens the member or role tab
- **THEN** read-only content remains available while owner-only mutation controls stay hidden or disabled as before

### Requirement: Legacy route compatibility
The system SHALL hide duplicate member and role routes from the primary menu and redirect direct visits to `/team/members` or `/team/roles` to `/team/my`.

#### Scenario: Visit legacy member route
- **WHEN** a user visits `/team/members`
- **THEN** the router redirects to `/team/my` and the member management tab can be selected there

#### Scenario: Visit legacy role route
- **WHEN** a user visits `/team/roles`
- **THEN** the router redirects to `/team/my` and the role management tab can be selected there
