# script-review-workflow-agent Specification

## Purpose
TBD - created by archiving change upgrade-script-review-workflow-agent. Update Purpose after archive.
## Requirements
### Requirement: Provide an enabled independent script-review workflow Agent
The system SHALL provide a workflow Agent identified by `script-review` that reviews one trusted immutable `review_script_version` through a `review_task` and SHALL keep its data scope independent from short-drama creation scripts and episodes.

#### Scenario: Start review for an imported draft
- **WHEN** an authorized user starts review for an imported review version
- **THEN** the system invokes the `script-review` workflow Agent against that review task and version
- **AND** does not read a main-project `script` row as the review source

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
A QUICK review SHALL execute one workflow Agent Run over the selected scope, SHALL prioritize evident local issues in the selected dimensions, and SHALL require one successful `save_review_result` call before completion.

#### Scenario: Quick review completes
- **WHEN** the Agent reads the trusted context, content, and any relevant history and successfully calls `save_review_result`
- **THEN** the task completes with the formal saved report
- **AND** the Run audit contains the ordered read and save tool steps

#### Scenario: Quick scope exceeds the safe context budget
- **WHEN** the complete selected QUICK scope cannot fit the configured safe model budget
- **THEN** the Run fails with `REVIEW_SCOPE_TOO_LARGE_FOR_QUICK`
- **AND** the system does not claim partial review coverage

### Requirement: Deep review uses child and aggregation Run contracts
A DEEP review SHALL use one child Run per frozen review unit and one final aggregation Run after all units succeed. Child Runs SHALL save only unit candidates, and only the aggregation Run SHALL save the formal report.

#### Scenario: Deep review starts for fifty-eight units
- **WHEN** the planner freezes fifty-eight in-scope review units
- **THEN** the coordinator schedules fifty-eight child Runs under the configured concurrency limit
- **AND** schedules no aggregation Run until all child terminal saves succeed

### Requirement: Enforce required tools and terminal-save behavior
The workflow runtime SHALL expose only the tools allowed for the Run phase, SHALL fail final text that lacks the required terminal save, and SHALL permit at most one successful terminal save per Run.

#### Scenario: Child Run attempts final formal save
- **WHEN** a DEEP child Run calls `save_review_result`
- **THEN** the scope guard rejects the tool as unavailable for that phase

#### Scenario: Model returns prose without saving
- **WHEN** a review Run returns final text without its required save tool succeeding
- **THEN** the Run fails with a required-tool error
- **AND** the task or unit is not marked successful

### Requirement: Preserve workflow Agent audit and AI execution correlation
Every review Agent Run SHALL persist model, prompt, Skill, tool, step, call-log, task, execution, attempt, phase, scope, and terminal outcome references without storing unbounded source text in diagnostic summaries.

#### Scenario: Inspect a completed review
- **WHEN** an administrator opens the Run audit for a completed review task
- **THEN** the audit identifies every frozen Skill and tool call and its correlated AI call log
- **AND** the formal task points to its terminal Agent Run

