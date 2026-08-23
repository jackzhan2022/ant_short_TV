## ADDED Requirements

### Requirement: Agent-aware unified invocation
The system SHALL allow supported business AI workflows to resolve a built-in Agent and composed Skills before entering the existing `AiInvocationService` provider invocation flow.

#### Scenario: Existing scene uses its Agent
- **WHEN** a workflow invokes a business scene that has a registered built-in Agent
- **THEN** the system renders that Agent's composed prompt
- **AND** passes the rendered request through the existing capability routing, provider adapter, error mapping, and call-log flow

#### Scenario: Unsupported scene has no Agent
- **WHEN** a workflow requests Agent resolution for an unregistered business scene
- **THEN** the system rejects the request with a normalized validation or unavailable-AI error
- **AND** it does not issue a provider call

### Requirement: Preserve workflow contracts during migration
The system SHALL preserve the existing required input variables, structured output expectations, and business outcome handling when current built-in prompt workflows are migrated to Agent resolution.

#### Scenario: Character extraction remains compatible
- **WHEN** the character extraction workflow executes through its built-in Agent
- **THEN** it accepts the existing script title and script content variables
- **AND** it preserves the existing characters JSON structure and business parsing behavior

#### Scenario: Business parsing failure remains observable
- **WHEN** a provider returns content that cannot satisfy the Agent's expected business output contract
- **THEN** the system records the provider call as a business failure using the existing AI response error semantics
- **AND** preserves the associated AI call-log identity when available
