## MODIFIED Requirements

### Requirement: Quick review uses one bounded Markdown-output Run
A QUICK review SHALL execute one workflow Agent Run over the selected scope, SHALL prioritize evident local issues in the selected dimensions, and SHALL complete only after its non-empty final Markdown output is persisted on the review task.

#### Scenario: Quick review completes
- **WHEN** the Agent reads the trusted context and content and returns non-empty final Markdown
- **THEN** the system stores that text as the task report and completes the task
- **AND** the Run audit contains the correlated model call and any trusted read steps

#### Scenario: Quick scope exceeds the safe context budget
- **WHEN** the complete selected QUICK scope cannot fit the configured safe model budget
- **THEN** the Run fails with `REVIEW_SCOPE_TOO_LARGE_FOR_QUICK`
- **AND** the system does not claim partial review coverage

### Requirement: Deep review uses Markdown child and aggregation Run contracts
A DEEP review SHALL use one child Run per frozen review unit and one final aggregation Run after all units succeed. Child Runs SHALL produce only persisted Markdown fragments, and only the aggregation Run SHALL produce the final Markdown report. The system SHALL update the review foundation, execution framework, and cross-episode synthesis Skills for Markdown execution while retaining the dimension Skills as the source of dimension-specific checks.

#### Scenario: Deep review starts for fifty-eight units
- **WHEN** the planner freezes fifty-eight in-scope review units
- **THEN** the coordinator schedules fifty-eight child Runs under the configured concurrency limit
- **AND** schedules no aggregation Run until all child Markdown fragments are saved successfully

#### Scenario: Markdown review Skills are loaded
- **WHEN** the system freezes a Markdown review execution plan
- **THEN** its foundation and execution Skills require final Markdown rather than terminal save tools
- **AND** DEEP aggregation loads the Markdown cross-episode synthesis rules
- **AND** semantic-quality Skill is not loaded

#### Scenario: Aggregation removes overlapping duplicates
- **WHEN** ordered Markdown fragments report the same dimension, root cause, and stable source location more than once
- **THEN** the aggregation report contains one merged finding with every distinct source citation
- **AND** similar wording with different root causes remains separate

### Requirement: Complete review Runs without a terminal write tool
The workflow runtime SHALL expose only trusted read capabilities allowed for the Run phase and SHALL accept non-empty final Markdown as the terminal result without requiring a model-invoked save tool.

#### Scenario: Model returns Markdown without calling save_review_result
- **WHEN** a new Markdown review Run returns non-empty final text after its required trusted reads
- **THEN** the Run succeeds without `save_review_result`
- **AND** the review adapter persists the text through the server-owned completion transaction

#### Scenario: Model attempts a legacy write tool
- **WHEN** a new Markdown review Run attempts `save_review_unit_result`, `save_review_semantic_decisions`, or `save_review_result`
- **THEN** the scope guard rejects the unavailable tool
- **AND** no structured review data is written
