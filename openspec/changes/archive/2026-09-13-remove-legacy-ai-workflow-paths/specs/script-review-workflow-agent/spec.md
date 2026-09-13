## MODIFIED Requirements

### Requirement: Provide an enabled independent script-review workflow Agent
The system SHALL use script-review for all QUICK and DEEP review tasks against their immutable review_script_version, independently of creation-project scripts. It SHALL use the current Markdown flow without direct-call fallback or enable switches.

#### Scenario: Start review for an imported draft
- **WHEN** an authorized user starts review for an imported version
- **THEN** the current Markdown workflow runs against the frozen review task and version
- **AND** it does not read a main-project script row as the source

#### Scenario: Review Agent is unavailable
- **WHEN** required current configuration is unavailable
- **THEN** the system reports the configuration failure without invoking the old review implementation

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
