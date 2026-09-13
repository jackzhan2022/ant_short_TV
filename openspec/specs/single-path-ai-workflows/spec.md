# single-path-ai-workflows Specification

## Purpose
TBD - created by archiving change remove-legacy-ai-workflow-paths. Update Purpose after archive.
## Requirements
### Requirement: Run the current workflows without routing or scheduling switches
The system SHALL use the current analysis Agents, scoped formal asset recognition, Markdown review and storyboard Agent as the sole implementations of their workflows. Business execution dispatchers, video schedulers and automatic storyboard dispatch SHALL start without deployment-level enable switches. Timeout, concurrency, retry, lease and interval parameters SHALL remain configurable. Unrelated payment, storage and test-provider settings SHALL remain outside this removal.

#### Scenario: Start without migration flags
- **WHEN** the application starts with valid required configuration and none of the retired enable variables
- **THEN** current workflows and their schedulers are available
- **AND** no configuration choice selects a legacy executor

#### Scenario: Required Agent configuration is unavailable
- **WHEN** a required Agent, Skill or compatible model is missing or disabled
- **THEN** the operation reports an actionable configuration error
- **AND** it does not invoke a legacy path or bypass authorization and accounting

### Requirement: Retire old API and configuration surfaces
The system SHALL remove legacy element extraction and candidate-review APIs, aggregate script-workspace and asset-settings-workspace APIs, and the old built-in/editable Agent and Skill management domain. All active consumers SHALL use the current focused APIs and configuration sources. Mutations SHALL return minimal results and refresh only required views rather than constructing retired aggregates.

#### Scenario: Caller requests an obsolete API
- **WHEN** a caller requests a retired endpoint
- **THEN** the endpoint is not mapped
- **AND** no legacy operation or redirected billable operation starts

#### Scenario: User edits a workbench resource
- **WHEN** a supported mutation succeeds
- **THEN** its consumer refreshes the affected focused data
- **AND** the backend does not assemble the removed aggregate workspace

#### Scenario: A supported text workflow used old definitions
- **WHEN** script generation, rewrite or prompt generation executes after retirement
- **THEN** its configuration resolves through its designated current source
- **AND** no hidden legacy definition service is required

### Requirement: Use Markdown as the only review result contract
QUICK and DEEP review SHALL use the current Markdown flow. The system SHALL remove structured issue, hit, matching, semantic-decision and anomaly-gate generation and historical structured-result presentation. DEEP SHALL preserve scoped unit results, ordering, retries, cancellation and final aggregation. Provider truncation and empty final output SHALL fail the corresponding unit or task.

#### Scenario: Valid report has no save tool call
- **WHEN** a review Run returns non-empty untruncated final Markdown
- **THEN** the backend persists that Markdown and completes the corresponding unit or task
- **AND** no structured terminal-save tool is required

#### Scenario: Deep unit fails
- **WHEN** a required DEEP unit fails
- **THEN** successful Markdown units remain reusable and final aggregation does not report success

### Requirement: Remove obsolete data with an explicit dependency manifest
The implementation SHALL provide an exact table, column, selection, dependency and deletion-order manifest for affected pre-release business data and obsolete configuration domains. Cleanup SHALL preserve accounts, teams, current permission boundaries, providers/models, Workflow Agent and file Skill configuration, financial integrity and unrelated business records. It SHALL resolve running claims and reservations before destructive migration and SHALL NOT leave dangling references or schedulable legacy work.

#### Scenario: Upgrade a populated development database
- **WHEN** cleanup runs after target verification and old application shutdown
- **THEN** listed obsolete records and schema objects are removed in dependency order
- **AND** preserved configuration and ledger invariants pass verification
- **AND** the cleanup report records actual affected counts and recovery snapshot information

#### Scenario: Shared accounting references a retired task
- **WHEN** a historical task has reservation or ledger dependencies
- **THEN** pending reservations are released through the accounting lifecycle and necessary immutable audit references are preserved
- **AND** the task cannot be dispatched again

### Requirement: Preserve migration reproducibility without compatibility execution
The schema cleanup SHALL use new Flyway migrations without rewriting applied migration history. Empty-database installation and populated-database upgrade SHALL reach the same current schema. Recovery SHALL restore a consistent snapshot and matching software instead of toggling a legacy execution switch.

#### Scenario: Install from an empty database
- **WHEN** all migrations run on an empty supported database
- **THEN** the final schema contains only current runtime structures plus required financial/audit records
- **AND** current bootstrap preserves or initializes the required configuration idempotently

