## MODIFIED Requirements

### Requirement: Platform administrators manage independent model billing prices
The system SHALL allow only authorized platform administrators to manage supplier cost prices and user point prices as independent rule sets for enabled platform models. Each rule set SHALL be selected by model, usage metric, normalized dimensions, and effective time. Model billing management SHALL open in a dialog from the corresponding AI-model list item; it SHALL receive that item as fixed model context and MUST NOT require or display a model selector.

#### Scenario: Administrator opens model billing from an AI model
- **WHEN** an authorized platform administrator opens the model-pricing dialog from an enabled AI-model list item
- **THEN** the system SHALL load supplier cost prices and user point prices for that model without allowing the administrator to select another model

#### Scenario: Non-platform user accesses billing management
- **WHEN** a user without the required platform billing permission requests a billing management API or page
- **THEN** the system SHALL deny the request and SHALL not expose supplier cost prices

## REMOVED Requirements

### Requirement: Standalone model selection on billing page
**Reason**: Model billing is managed from the AI-model list so that prices are always associated with a known model.
**Migration**: Use the AI-model list's current-price columns or model-pricing action to open the dialog; legacy billing URLs redirect to the AI-model tab.
