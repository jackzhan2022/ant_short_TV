# ai-usage-cost-accounting Specification

## Purpose
TBD - created by archiving change unify-ai-execution-and-billing. Update Purpose after archive.

## Requirements

### Requirement: AI usage is captured as structured meter lines
The system SHALL record immutable usage lines for every billable provider interaction using supported metrics such as `CALL`, `INPUT_TOKEN`, `OUTPUT_TOKEN`, `IMAGE`, `VIDEO_SECOND`, `AUDIO_SECOND`, and `CHARACTER`.

#### Scenario: Text model reports token usage
- **WHEN** a text provider reports prompt and completion token counts
- **THEN** the call log receives separate `INPUT_TOKEN` and `OUTPUT_TOKEN` usage lines with the reported quantities

#### Scenario: Image or video usage is measured
- **WHEN** an image or video provider completes billable generation
- **THEN** the system records image count or generated duration as structured usage instead of relying on summary text

### Requirement: Model prices are versioned and effective-dated
The system SHALL support multiple price components per model, with metric, unit size, unit price, currency, optional pricing dimensions, effective interval, and immutable version identity.

#### Scenario: Composite token pricing is configured
- **WHEN** a model has different input-token and output-token prices
- **THEN** both price components can be active for the same model and effective interval

#### Scenario: A future price is published
- **WHEN** an operator configures a new price version with a future effective time
- **THEN** existing calls continue using the current version until the future version becomes effective

### Requirement: Cost uses an immutable price snapshot
The system SHALL snapshot the resolved price component onto each priced usage line and calculate cost from quantity, unit size, and unit price without changing historical cost when model pricing is later edited.

#### Scenario: Historical price is changed
- **WHEN** an operator publishes a replacement price after a call has been costed
- **THEN** the prior usage line retains its original price version, unit price, currency, and calculated cost

### Requirement: Composite and dimensional pricing is supported
The system SHALL sum all applicable usage-line costs and SHALL support pricing dimensions needed by a metric, such as image size, video resolution, quality tier, or model variant.

#### Scenario: Video price depends on duration and resolution
- **WHEN** a 1080p video model is priced per generated second
- **THEN** the system selects the matching dimensional price and calculates cost using generated seconds

#### Scenario: One invocation has multiple meters
- **WHEN** a provider charges both per call and per generated image
- **THEN** the invocation records and sums both cost lines

### Requirement: Missing usage or pricing is explicit
The system SHALL mark an invocation cost as `UNPRICED` or `INCOMPLETE` when required usage or an effective price is unavailable and SHALL NOT silently substitute zero cost.

#### Scenario: No effective price exists
- **WHEN** a billable usage metric has no matching effective model price
- **THEN** the cost status identifies the missing price and operations can reconcile it later

### Requirement: Cost aggregation is auditable
The system SHALL aggregate costs by tenant, project, business scene, model, provider, task, time interval, and currency while retaining drill-down to immutable usage lines and call logs.

#### Scenario: Operator reviews project cost
- **WHEN** an operator requests project AI cost for a time interval
- **THEN** the result is derived from usage cost lines and can be reconciled to each provider call without mixing currencies implicitly

### Requirement: Cost corrections preserve history
The system SHALL correct usage or price errors through explicit adjustment records rather than destructive edits to settled historical lines.

#### Scenario: Provider reports corrected usage
- **WHEN** a provider reconciliation changes a previously recorded usage quantity
- **THEN** the system records an adjustment linked to the original line and exposes the net cost with both records retained

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
