## ADDED Requirements

### Requirement: AI attempts use unified invocation outcomes
The system SHALL link asynchronous AI task attempts to unified invocation outcomes, including AI call log id, provider request id, normalized error code, and business outcome.

#### Scenario: Async invocation succeeds
- **WHEN** an asynchronous AI task phase completes through the unified invocation contract
- **THEN** the execution attempt records the returned AI call log id and provider request id

#### Scenario: Provider call fails during async invocation
- **WHEN** an asynchronous AI task phase fails due to a provider-level invocation error
- **THEN** the execution attempt records the normalized error code and remains linked to the failed AI call log when one was created

#### Scenario: Business parsing fails during async invocation
- **WHEN** an asynchronous AI task receives a provider success but business parsing fails
- **THEN** the execution attempt is marked failed with `AI_RESPONSE_INVALID` and remains linked to the AI call log for the real provider call
