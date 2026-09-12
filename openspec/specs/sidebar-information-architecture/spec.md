# sidebar-information-architecture Specification

## Purpose
TBD - created by archiving change reorganize-sidebar-and-team-invitations. Update Purpose after archive.
## Requirements
### Requirement: Grouped primary navigation
The primary navigation SHALL display four groups in order: Creation, Mine, Management, and Commercial, with the specified pages nested under each group. Mine SHALL additionally contain a Task Center entry at `/tasks` for active team members, defaulting to their own tasks. Owners and active system administrators SHALL have a team-scope switch within the center; ordinary members SHALL not have that switch.

#### Scenario: View authorized navigation
- **WHEN** an authenticated user opens the application shell
- **THEN** the groups appear in the required order and each authorized page appears under its designated group

#### Scenario: Preserve route URLs
- **WHEN** a user selects a grouped menu item
- **THEN** the application navigates to the existing page URL for that feature

#### Scenario: Open task center
- **WHEN** an active team member selects Task Center under Mine
- **THEN** `/tasks` opens in mine scope with the team switch available only to the current owner or active system administrator

