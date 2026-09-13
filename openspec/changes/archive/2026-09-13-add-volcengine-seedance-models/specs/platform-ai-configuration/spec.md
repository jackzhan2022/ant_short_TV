## ADDED Requirements

### Requirement: Built-in Seedance model catalogue is available without a new configuration surface
The platform SHALL seed the Volcengine Ark Provider and the three Seedance Models as application-owned built-in definitions. Endpoint IDs SHALL remain application-owned values rather than a new Model-management API or UI field.

#### Scenario: Platform administrator opens model management before Endpoint IDs are supplied
- **WHEN** an authorized platform administrator views the AI-model list after deployment
- **THEN** the three Seedance Model definitions are visible as disabled models and no Endpoint-ID editing control is exposed

#### Scenario: Endpoint IDs are supplied after deployment
- **WHEN** developers replace the application-owned placeholders with the supplied Endpoint IDs and deploy the change
- **THEN** the existing Provider credential and Model enablement workflows remain the only platform configuration actions required before routing can use the models
