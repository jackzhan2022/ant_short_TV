## ADDED Requirements

### Requirement: Asset settings page presents a visible initial loading state
The asset settings page SHALL present a layout-matched skeleton while its initial asset workspace and pending-candidate data are loading. It SHALL NOT present an empty asset page during that initial request.

#### Scenario: Initial settings data is pending
- **WHEN** a user opens the asset settings page and either initial request has not completed
- **THEN** the page displays the settings-page skeleton and loading context
- **AND** it does not display the empty-asset state as final content

### Requirement: Asset settings page uses the lightweight workspace endpoint
The asset settings page SHALL obtain its asset collections from the asset settings workspace endpoint on initial load and after settings-page mutations refresh data.

#### Scenario: Initial load succeeds
- **WHEN** the asset settings workspace and pending-candidate requests succeed
- **THEN** the page replaces the skeleton with the returned asset collections and candidate count

#### Scenario: Initial load fails
- **WHEN** either initial settings data request fails
- **THEN** the page stops displaying the loading skeleton
- **AND** it displays a recoverable load-failure state with a retry action
