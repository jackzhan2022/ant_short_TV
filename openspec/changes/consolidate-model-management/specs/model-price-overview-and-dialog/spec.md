## ADDED Requirements

### Requirement: AI model list displays current prices
The system SHALL display a read-only current cost-price column and a read-only current point-price column for every AI model in the AI-model list. Each column SHALL show the active price summary for its price type at the current time, or an explicit empty state when no active version exists. Neither price column SHALL open or change model pricing.

#### Scenario: Model has active price versions
- **WHEN** the AI-model list loads a model with an active cost-price version and an active point-price version
- **THEN** the system SHALL display the respective current price summaries in the cost-price and point-price columns

#### Scenario: Model has no active point price
- **WHEN** the AI-model list loads a model without an active point-price version
- **THEN** the system SHALL display the configured empty-state value in the point-price column

### Requirement: Model price management opens in model context
The system SHALL open model price management in a dialog bound to the selected AI model when a user selects that model's model-pricing action. The dialog SHALL display that model's independent cost-price and point-price version histories and SHALL not permit switching to another model.

#### Scenario: User opens model pricing from the model list
- **WHEN** an authorized user selects the model-pricing action for an AI model
- **THEN** the system SHALL open the model-pricing dialog for that model and show the corresponding price type

#### Scenario: User manages prices in the dialog
- **WHEN** a user with model-billing publication permission opens a model-pricing dialog
- **THEN** the system SHALL expose the existing publish and eligible future-version revoke controls for the selected model only

### Requirement: Price summaries refresh after management changes
The system SHALL refresh the selected model's displayed current cost price and current point price after a successful price publication or revocation in the model-pricing dialog.

#### Scenario: User publishes a new price version
- **WHEN** an authorized user successfully publishes a price version from the model-pricing dialog
- **THEN** the system SHALL refresh the selected model's price summaries after the dialog data refresh completes
