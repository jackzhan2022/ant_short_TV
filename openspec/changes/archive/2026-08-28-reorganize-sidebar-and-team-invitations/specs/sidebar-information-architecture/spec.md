## ADDED Requirements

### Requirement: Grouped primary navigation
The primary navigation SHALL display four groups in order: Creation, Mine, Management, and Commercial, with the specified pages nested under each group.

#### Scenario: View authorized navigation
- **WHEN** an authenticated user opens the application shell
- **THEN** the groups appear in the required order and each authorized page appears under its designated group

#### Scenario: Preserve route URLs
- **WHEN** a user selects a grouped menu item
- **THEN** the application navigates to the existing page URL for that feature
