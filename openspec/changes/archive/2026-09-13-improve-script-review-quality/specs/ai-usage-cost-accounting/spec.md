## ADDED Requirements

### Requirement: Cached text input usage is preserved and priced distinctly
The system SHALL capture provider-reported ordinary input tokens, cache-read tokens, and cache-write tokens as distinct immutable usage quantities while preserving the provider's total input-token count. Missing cache detail SHALL remain explicitly unknown and MUST NOT be converted to a reported zero.

#### Scenario: Provider reports a cache hit
- **WHEN** a GPT text response reports total input tokens and cached input-token details
- **THEN** the call log and usage lines preserve the reported cached quantity
- **AND** ordinary input is derived only according to the provider's documented usage relationship

#### Scenario: Provider omits cache detail
- **WHEN** a compatible provider reports only total prompt tokens
- **THEN** total input usage remains billable and auditable
- **AND** cache-read and cache-write quantities are marked unavailable rather than zero

### Requirement: Prompt-cache identity and performance are auditable
The system SHALL associate each eligible model call with a non-secret cache identity, model, provider, review attempt, dimension, latency, and reported cache quantities and SHALL expose cache hit ratio without storing API keys or unrestricted source text.

#### Scenario: Operator inspects dimensional review cost
- **WHEN** an operator views usage for a completed dimensional review
- **THEN** the system shows input, cached input, cache-write, output, latency, and cache hit ratio per dimension and in aggregate
- **AND** every quantity drills down to its immutable call log

### Requirement: Cache pricing uses effective model components
The system SHALL support effective-dated price components for ordinary text input, cached input reads, and cache writes and SHALL mark cost incomplete when a reported cache metric lacks an applicable price component.

#### Scenario: Cached input has a reduced rate
- **WHEN** a model call reports cached input and an effective cached-input price exists
- **THEN** the system prices that quantity using the cached-input component
- **AND** does not charge the same quantity again as ordinary input
