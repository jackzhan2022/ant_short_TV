## MODIFIED Requirements

### Requirement: Register trusted review workflow tools for dual-format compatibility
The system SHALL keep the existing review workflow tools registered for historical structured execution and rollback, SHALL expose only trusted context and bounded content reads to new Markdown execution plans, and SHALL exclude candidate, semantic-decision, history, and formal-result write tools from those new plans.

#### Scenario: Markdown execution plan is created
- **WHEN** the server freezes a new Markdown review attempt
- **THEN** its phase allowlist contains only the trusted reads required for that phase
- **AND** excludes `read_review_issue_history`, `save_review_unit_result`, `read_review_unit_results`, `read_review_candidates`, `save_review_semantic_decisions`, and `save_review_result`

#### Scenario: Historical structured execution remains recoverable
- **WHEN** a pre-existing structured attempt is resumed during the compatibility period
- **THEN** the legacy tool definitions remain available under its frozen execution contract

## REMOVED Requirements

### Requirement: Save unit candidates without promoting formal issues
**Reason**: New DEEP reviews persist free-form Markdown fragments and no longer create model-validated candidate documents.

**Migration**: Existing candidate rows and legacy execution contracts remain readable; new Markdown plans do not expose `save_review_unit_result`.

### Requirement: Read only complete current unit candidates
**Reason**: New aggregation consumes server-owned ordered Markdown fragments rather than candidate JSON.

**Migration**: `read_review_unit_results` remains registered only for frozen legacy structured attempts and is not used by Markdown aggregation.

### Requirement: Save the formal review result atomically
**Reason**: New review completion saves the model's final Markdown through a server-owned transaction instead of a model-invoked structured save tool.

**Migration**: Historical structured reports remain unchanged; Markdown reports use the explicit task result format and Markdown column.

### Requirement: Compute issue identity and lifecycle on the server
**Reason**: New Markdown reviews do not create formal issue rows or automatic cross-round lifecycle states.

**Migration**: Existing issue identities, lifecycle events, and manual-resolution history remain available for historical structured tasks.

