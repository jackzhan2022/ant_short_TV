## ADDED Requirements

### Requirement: Register six trusted review workflow tools
The system SHALL register `read_review_context`, `read_review_content`, `read_review_issue_history`, `save_review_unit_result`, `read_review_unit_results`, and `save_review_result` with strict input and output schemas and SHALL expose them only through phase-specific Agent allowlists.

#### Scenario: Tool catalog starts
- **WHEN** the application initializes the workflow tool catalog
- **THEN** all six unique review tool codes are available to authorized review execution plans
- **AND** catalog validation fails on duplicate codes or invalid schemas

### Requirement: Derive review identity and authorization from trusted Run scope
Review tools SHALL derive tenant, user, review project, task, version, unit, attempt, and phase from trusted execution context and MUST reject model-supplied business identity or access outside the frozen scope.

#### Scenario: Model supplies a foreign version identifier
- **WHEN** tool arguments contain a tenant, project, task, version, unit, or user identifier
- **THEN** schema validation or scope validation rejects the call
- **AND** no foreign data is returned or modified

### Requirement: Read frozen review context
`read_review_context` SHALL return the immutable version hash, scope hash, mode, round, selected dimensions, structural counts, required coverage, safe snapshot keys, and current phase without returning unrelated project data.

#### Scenario: Child Run reads context
- **WHEN** a DEEP child Run calls `read_review_context`
- **THEN** the result identifies its one frozen unit and selected dimensions
- **AND** excludes content outside the frozen review scope

### Requirement: Read only bounded in-scope review content
`read_review_content` SHALL return bounded content and stable episode, scene, line, character-offset, and anchor metadata only from the frozen review version and scope. Scene scope SHALL be enforced as a real content filter.

#### Scenario: Review selected scenes
- **WHEN** the task scope contains selected scene keys
- **THEN** the tool returns only those scenes and their trusted location metadata
- **AND** does not fall back to the complete script

#### Scenario: Read heading-free deep-review unit
- **WHEN** a DEEP unit was planned from character offsets because no reliable episode headings exist
- **THEN** the tool returns the exact frozen offset range and stable unit fingerprint
- **AND** does not create formal episode records

### Requirement: Read bounded relevant issue history
`read_review_issue_history` SHALL return prior-round issues and hits filtered by the current selected dimensions and scope, including manual-resolution history, and SHALL paginate or bound large histories.

#### Scenario: First review round
- **WHEN** the current task has no prior round
- **THEN** the tool returns an empty issue list with a successful result

#### Scenario: Scoped repeat review
- **WHEN** a later round reviews one episode and one dimension
- **THEN** the tool returns only relevant prior issues and trusted hit anchors for that scope and dimension

### Requirement: Save unit candidates without promoting formal issues
`save_review_unit_result` SHALL validate the child phase, frozen hashes, selected dimensions, coverage, candidate sizes, locations, and evidence before atomically storing one current candidate document for that snapshot unit and Run.

#### Scenario: Child candidate save succeeds
- **WHEN** a child Run submits valid in-scope candidates for its complete unit
- **THEN** the system stores the candidate document and marks its unit terminal-save coverage successful
- **AND** creates no `review_issue` rows

#### Scenario: One candidate cites absent evidence
- **WHEN** any candidate excerpt or anchor cannot be verified in the unit content
- **THEN** the entire tool call fails
- **AND** no partial candidate document is committed

### Requirement: Read only complete current unit candidates
`read_review_unit_results` SHALL be available only to the matching DEEP aggregation Run and SHALL return bounded candidates and coverage from the unchanged current snapshot.

#### Scenario: A unit is failed or missing
- **WHEN** the aggregation Run requests candidates before every unit has successful terminal-save coverage
- **THEN** the tool fails with `REVIEW_UNITS_INCOMPLETE`
- **AND** returns no partial aggregation input

### Requirement: Save the formal review result atomically
`save_review_result` SHALL verify the frozen version, scope, dimensions, phase, required coverage, score, conclusion, severity, locations, evidence, issue uniqueness, and multi-hit structure before atomically writing the task result, formal issues, hits, and events.

#### Scenario: Valid QUICK result is saved
- **WHEN** a QUICK Run submits a valid result covering its complete frozen scope
- **THEN** the system writes the formal report in one transaction
- **AND** marks the terminal save successful for that Run

#### Scenario: Deep snapshot is incomplete
- **WHEN** an aggregation Run attempts final save while any frozen unit is failed, missing, stale, or lacks candidate-save coverage
- **THEN** the tool rejects the save
- **AND** leaves all existing formal reports unchanged

#### Scenario: Formal write fails midway
- **WHEN** any task, issue, hit, event, or matching write fails during final save
- **THEN** the complete transaction rolls back
- **AND** the task is not marked completed

### Requirement: Compute issue identity and lifecycle on the server
The final save service SHALL assign issue numbers and SHALL deterministically compute `new`, `persists`, `shifted`, `fixed`, or `uncertain` from prior formal issues, stable anchors, dimensions, evidence, and multiple hits. Model-supplied identity and lifecycle values MUST NOT be trusted as database identity.

#### Scenario: Previously resolved issue persists
- **WHEN** a new result matches a manually resolved prior issue at a trusted anchor
- **THEN** the new round marks it `persists`
- **AND** preserves the earlier manual-resolution event

#### Scenario: Two prior issues are equally plausible matches
- **WHEN** deterministic matching cannot choose one prior identity safely
- **THEN** the new issue is marked `uncertain`
- **AND** no arbitrary prior issue is linked

### Requirement: Review Agent tools cannot mutate scripts or user workflow actions
No review Agent tool SHALL edit review-version content, apply batch repair, resolve an issue, export a report, bind a project, or roll back a version.

#### Scenario: Agent attempts repair
- **WHEN** the model attempts to call a repair or version-edit action
- **THEN** no such tool is present in its allowlist
- **AND** script modification remains an explicit authorized user action
