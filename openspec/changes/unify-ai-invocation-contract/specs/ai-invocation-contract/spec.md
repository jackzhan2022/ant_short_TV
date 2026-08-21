## ADDED Requirements

### Requirement: Unified AI invocation entrypoint
The system SHALL provide a backend AI invocation entrypoint that accepts typed capability, business scene, tenant, user, project, task, trace, model, prompt, and payload metadata for business AI calls.

#### Scenario: Business caller invokes text generation
- **WHEN** a script workflow requests text generation through the unified invocation entrypoint
- **THEN** the system routes the request using the typed text capability and returns the provider content, provider request id, resolved model id, and AI call log id

#### Scenario: Business caller invokes video understanding
- **WHEN** a video decomposition workflow requests video understanding through the unified invocation entrypoint
- **THEN** the system routes the request using the typed video-understanding capability and returns the provider content, provider request id, resolved model id, and AI call log id

#### Scenario: Unsupported capability is requested
- **WHEN** a business caller requests a capability that has no enabled model or supported adapter
- **THEN** the system rejects the call with a normalized AI unsupported or unavailable error before writing a misleading success result

### Requirement: Stable AI business scene registry
The system SHALL define stable business scene identifiers for AI workflows and require migrated callers to use those identifiers instead of raw scene strings.

#### Scenario: Element extraction records scene code
- **WHEN** character, scene, or prop extraction performs an AI call
- **THEN** the AI call log records the matching stable business scene code for that extraction type

#### Scenario: Video draft generation records scene code
- **WHEN** video decomposition generates a screenplay draft from structured analysis
- **THEN** the AI call log records the stable video script draft business scene code

#### Scenario: Scene metadata is available for point consumption
- **WHEN** a migrated workflow consumes points for an AI call
- **THEN** the point transaction uses the same stable business scene code as the corresponding AI invocation

### Requirement: Prompt template rendering entrypoint
The system SHALL provide a prompt template rendering entrypoint for built-in AI prompts used by migrated workflows.

#### Scenario: Template renders required variables
- **WHEN** a migrated workflow renders a prompt template with all required variables
- **THEN** the system returns the rendered prompt used for the provider request

#### Scenario: Required variable is missing
- **WHEN** a migrated workflow renders a prompt template without a required variable
- **THEN** the system rejects the invocation with a validation error before calling the provider

#### Scenario: Strict JSON prompt is rendered
- **WHEN** an element extraction or video understanding prompt is rendered
- **THEN** the rendered prompt includes the required structured-output instructions for the expected response schema

### Requirement: Consistent AI call logging
The system SHALL record AI calls through a shared call-log lifecycle for provider success, provider failure, and business parsing failure.

#### Scenario: Provider call succeeds
- **WHEN** a migrated AI invocation receives a successful provider response
- **THEN** the system records provider, capability, model, business scene, task id, trace id, request summary, response summary, duration, provider request id, token usage when available, and success status in `ai_call_log`

#### Scenario: Provider call fails
- **WHEN** a migrated AI invocation fails due to provider auth, quota, rate limit, timeout, unsupported model, or provider error
- **THEN** the system records a failed AI call log with normalized error details and rethrows a normalized business exception

#### Scenario: Business parsing fails after provider success
- **WHEN** a migrated workflow receives a provider response but fails response parsing or normalization
- **THEN** the system keeps the provider request details linked to the AI call log and marks the business outcome as failed for diagnostics

### Requirement: Normalized AI errors
The system SHALL normalize AI invocation failures into stable application error codes and diagnostic categories.

#### Scenario: Provider rate limit
- **WHEN** a provider returns a rate-limit response during a migrated invocation
- **THEN** the caller receives `AI_RATE_LIMIT` and the AI call log records the normalized rate-limit failure

#### Scenario: Provider response is invalid
- **WHEN** a provider response is missing required content or cannot be parsed by the migrated business workflow
- **THEN** the caller receives `AI_RESPONSE_INVALID` and the AI call log remains available for troubleshooting

#### Scenario: Provider timeout
- **WHEN** a provider request times out during a migrated invocation
- **THEN** the caller receives `AI_PROVIDER_TIMEOUT` and the AI call log records the timeout outcome

### Requirement: Existing public APIs remain compatible
The system SHALL preserve existing frontend and public backend workflow APIs while migrating their internal AI invocation path.

#### Scenario: Script element extraction API is called
- **WHEN** an authorized user calls the existing script element extraction endpoint
- **THEN** the endpoint response shape remains compatible and the internal AI call uses the unified invocation contract

#### Scenario: Video decomposition API is called
- **WHEN** an authorized user starts or retries video decomposition through the existing endpoint
- **THEN** the endpoint response shape remains compatible and migrated AI calls use the unified invocation contract
