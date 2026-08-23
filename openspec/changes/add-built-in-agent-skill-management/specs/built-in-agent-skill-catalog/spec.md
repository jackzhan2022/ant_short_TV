## ADDED Requirements

### Requirement: Built-in Agent registry
The system SHALL provide a code-defined registry of built-in Agents, where each Agent has a stable code, display name, description, business scene, AI capability, input contract, output contract, and deterministic prompt definition.

#### Scenario: List built-in Agents
- **WHEN** an authenticated user with the AI management view permission requests the Agent catalog
- **THEN** the system returns all registered Agents in deterministic sort order
- **AND** each Agent includes its business scene, capability, and referenced Skill summaries

#### Scenario: Resolve an Agent by business scene
- **WHEN** an AI workflow invokes a supported business scene
- **THEN** the system resolves exactly one built-in Agent for that scene
- **AND** the invocation fails with a validation error if no Agent is registered

### Requirement: Built-in Skill registry
The system SHALL provide a code-defined registry of reusable, read-only Skills, where each Skill has a stable code, display metadata, category, and prompt content.

#### Scenario: List built-in Skills
- **WHEN** an authenticated user with the AI management view permission requests the Skill catalog
- **THEN** the system returns all registered Skills in deterministic sort order
- **AND** each Skill includes the built-in Agents that reference it

#### Scenario: Reject an invalid Skill reference
- **WHEN** application startup or registry validation encounters an Agent referencing an unknown Skill
- **THEN** the system fails registry validation
- **AND** it does not expose that Agent as a partially configured runtime definition

### Requirement: Fixed Agent-Skill composition
The system SHALL compose each Agent prompt from the Agent prompt definition and its fixed, ordered Skill references.

#### Scenario: Render a composed prompt
- **WHEN** an Agent is rendered with valid input variables
- **THEN** the system includes the Agent prompt content and each referenced Skill content in declared order
- **AND** variable substitution occurs using the existing prompt variable validation semantics

#### Scenario: Missing required variable
- **WHEN** an Agent render request omits a required input variable
- **THEN** the system rejects the request with a validation error
- **AND** it does not issue a provider call

### Requirement: Read-only catalog and preview
The system SHALL expose read-only APIs and a two-tab frontend view for browsing Agent and Skill definitions, relationships, contracts, and rendered prompt previews.

#### Scenario: View Agent detail
- **WHEN** a user opens an Agent from the Agent tab
- **THEN** the system displays its business scene, capability, input contract, output contract, ordered Skills, and immutable status

#### Scenario: Preview an Agent prompt
- **WHEN** a user submits valid preview variables for an Agent
- **THEN** the system returns the composed prompt
- **AND** it does not call an external AI provider or create an AI call log

#### Scenario: Mutation is unavailable
- **WHEN** a client attempts to create, update, delete, enable, disable, or version an Agent or Skill
- **THEN** the system does not expose a corresponding mutation operation
- **AND** the built-in definition remains unchanged

### Requirement: Platform-routed Agent execution
The system SHALL execute built-in Agents through the existing unified AI invocation service and SHALL resolve the model through platform capability routing.

#### Scenario: Agent follows the platform default model
- **WHEN** an Agent invokes a capability without a model override
- **THEN** `AiModelRouter` resolves the enabled platform model for that capability
- **AND** the Agent does not select or persist a provider API configuration

#### Scenario: Invocation records Agent context
- **WHEN** a built-in Agent is executed
- **THEN** the resulting invocation metadata identifies the Agent and business scene where the existing logging contract supports that metadata
- **AND** existing provider, model, duration, token, and outcome logging remains unchanged
