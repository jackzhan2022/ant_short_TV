## ADDED Requirements

### Requirement: Freeze reusable episode context
The system SHALL create an immutable tenant-scoped context from common rules, optional frozen global understanding, and current episode source with stable anchors. It SHALL NOT include generated episode summaries, run IDs, timestamps, mutable progress, or dynamic asset catalogs in the common prefix.

#### Scenario: Three stages share an unchanged episode
- **WHEN** summary, recognition, and storyboard run for the same context and model
- **THEN** their common serialized prefix and cache key are identical
- **AND** stage instructions and dynamic results appear only after that prefix

#### Scenario: Source changes or global understanding is absent
- **WHEN** the source changes
- **THEN** a new context identity is used and old-source saves are rejected
- **AND** an independently authorized run without global understanding can use an explicit absent value

### Requirement: Preload trusted source before model invocation
The system SHALL prepare trusted source and evidence state before the first model request, audit the read, and preserve source, ownership, terminal-save and stage tool checks.

#### Scenario: First summary request
- **WHEN** the first model request starts
- **THEN** it already contains the trusted episode source without a model round solely to request that read
- **AND** a successful validated save remains required for completion

#### Scenario: Cross-stage tool attempt
- **WHEN** the model requests a tool outside its active stage permissions
- **THEN** the server rejects it before any mutation even if its schema is visible

### Requirement: Measure actual cache and timing outcomes
The system SHALL record model identity, context identity, reported cache tokens and separate queue, preparation, model and persistence durations, with unknown provider measurements represented as unknown.

#### Scenario: Provider omits cache usage
- **WHEN** no cache usage or first-byte measurement is returned
- **THEN** the corresponding metrics remain unknown and are excluded from reported known-only ratios

#### Scenario: Different model or unsupported cache parameter
- **WHEN** a stage uses a different model or unsupported cache capability
- **THEN** the request remains valid and no cross-model cache hit is claimed
