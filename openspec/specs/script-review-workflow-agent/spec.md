# script-review-workflow-agent Specification

## Purpose
TBD - created by archiving change upgrade-script-review-workflow-agent. Update Purpose after archive.
## Requirements
### Requirement: Provide an enabled independent script-review workflow Agent
The system SHALL use script-review for all QUICK and DEEP review tasks against their immutable review_script_version, independently of creation-project scripts. It SHALL use the current Markdown flow without direct-call fallback or enable switches.

#### Scenario: Start review for an imported draft
- **WHEN** an authorized user starts review for an imported version
- **THEN** the current Markdown workflow runs against the frozen review task and version
- **AND** it does not read a main-project script row as the source

#### Scenario: Review Agent is unavailable
- **WHEN** required current configuration is unavailable
- **THEN** the system reports the configuration failure without invoking the old review implementation

### Requirement: Compose review Skills from trusted selected dimensions
The system SHALL load `script-review-foundation` and `script-review-execution-framework` followed only by the dimension Skills mapped by the server from the task's selected dimensions. A deep aggregation Run SHALL additionally load `script-review-cross-episode-synthesis`.

#### Scenario: Review only dialogue rationality
- **WHEN** the frozen task selects only dialogue rationality
- **THEN** the Run loads `script-review-dimension-dialogue` after the two common Skills
- **AND** does not load any of the other twelve dimension Skills

#### Scenario: Review multiple dimensions
- **WHEN** the frozen task selects dialogue, timeline, and prop continuity
- **THEN** the Run loads exactly those three dimension Skills after the common Skills
- **AND** preserves all loaded Skill revisions in the Run audit

#### Scenario: Client supplies an unknown Skill code
- **WHEN** a request attempts to select a dimension or Skill outside the server-owned mapping
- **THEN** the task is rejected before the Agent Run starts

### Requirement: Freeze Agent, Skill, model, scope, and mode configuration
Each review attempt SHALL freeze the Agent revision, ordered Skill revisions, model configuration, review mode, selected dimensions, review scope, version hash, and phase-specific tool allowlist before its first model call.

#### Scenario: Administrator publishes a Skill during review
- **WHEN** a Skill revision changes after a review attempt starts
- **THEN** every Run in that attempt continues with the frozen revision set
- **AND** a later explicit regeneration can use the new revision

### Requirement: Quick review uses one bounded terminal-save Run
A QUICK review SHALL execute one bounded workflow Run over its selected scope and SHALL complete when the backend persists a non-empty untruncated final Markdown report. It SHALL NOT require a terminal-save tool or structured issues.

#### Scenario: Quick review completes
- **WHEN** the Run produces valid final Markdown from its trusted scope
- **THEN** the backend saves it and completes the task with correlated model and Run audit

#### Scenario: Quick scope exceeds the safe context budget
- **WHEN** the selected QUICK scope exceeds the safe model budget
- **THEN** the operation fails with REVIEW_SCOPE_TOO_LARGE_FOR_QUICK and does not claim partial coverage

### Requirement: Deep review uses child and aggregation Run contracts
A DEEP review SHALL retain current frozen dimensions, scoped unit planning and bounded concurrency. Child Runs SHALL persist Markdown unit reports and the final aggregation SHALL persist the full Markdown report only after all required units succeed. Structured candidate and semantic-decision stages SHALL be removed.

#### Scenario: Deep review starts for fifty-eight units
- **WHEN** the planner freezes fifty-eight required units
- **THEN** it schedules the units within the concurrency limit and aggregates their ordered Markdown only after all succeed

### Requirement: Enforce required tools and terminal-save behavior
Review Runs SHALL enforce trusted source scope and allowed read tools. Completion SHALL depend on persisted non-empty untruncated Markdown rather than a model-called terminal save. Structured result/candidate/decision write tools SHALL NOT be exposed by the current review workflow.

#### Scenario: Child Run attempts structured formal save
- **WHEN** a child Run attempts save_review_result
- **THEN** the call is rejected as unavailable

#### Scenario: Model returns Markdown without saving
- **WHEN** a Run returns non-empty untruncated final Markdown without a save-tool call
- **THEN** the backend persists it and marks the corresponding unit or task successful

#### Scenario: Model returns empty or truncated content
- **WHEN** final output is empty or provider truncation is reported
- **THEN** the corresponding unit or task fails and retains an actionable error

### Requirement: Preserve workflow Agent audit and AI execution correlation
Every review Agent Run SHALL persist model, prompt, Skill, tool, step, call-log, task, execution, attempt, phase, scope, and terminal outcome references without storing unbounded source text in diagnostic summaries.

#### Scenario: Inspect a completed review
- **WHEN** an administrator opens the Run audit for a completed review task
- **THEN** the audit identifies every frozen Skill and tool call and its correlated AI call log
- **AND** the formal task points to its terminal Agent Run

