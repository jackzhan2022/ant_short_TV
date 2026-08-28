# team-invitation-management-tab Specification

## Purpose
TBD - created by archiving change reorganize-sidebar-and-team-invitations. Update Purpose after archive.
## Requirements
### Requirement: Invitation management tab
The team management page SHALL provide an Invitation Management tab that contains the existing received-invitations and sent-invitations views.

#### Scenario: Open invitation tab
- **WHEN** a user selects Invitation Management in team management
- **THEN** received and sent team invitation views are available without leaving the page

### Requirement: Legacy invitation compatibility
The existing `/team/invitations` route SHALL be hidden from the primary menu and redirect to team management.

#### Scenario: Visit legacy invitation route
- **WHEN** a user visits `/team/invitations`
- **THEN** the router redirects to `/team/my` while invitation management remains accessible as a tab

