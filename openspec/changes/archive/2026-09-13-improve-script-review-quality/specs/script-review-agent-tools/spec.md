## MODIFIED Requirements

### Requirement: Save unit candidates without promoting formal issues
`save_review_unit_result` SHALL validate the discovery phase, frozen hashes, selected dimension, coverage, candidate sizes, locations, and evidence before atomically storing the complete submitted candidate document for that dimension and Run. Mechanically invalid candidates MUST NOT disappear silently: the tool SHALL fail the atomic submission with actionable per-candidate errors or persist explicit invalid-candidate audit records according to the bounded correction policy.

#### Scenario: Dimension candidate save succeeds
- **WHEN** a dimension Run submits valid in-scope candidates for complete coverage
- **THEN** the system stores the candidate document and marks that dimension's terminal-save coverage successful
- **AND** creates no formal `review_issue` rows before semantic review and aggregation

#### Scenario: One candidate cites absent evidence
- **WHEN** any candidate excerpt or anchor cannot be verified in frozen content
- **THEN** the tool returns the candidate-specific validation failure without committing a misleading successful empty result
- **AND** the failed candidate and correction outcome remain traceable to the Run

### Requirement: Save the formal review result atomically
`save_review_result` SHALL verify the frozen version, scope, dimensions, phase, required per-dimension coverage, semantic decisions, anomaly-gate outcome, score, conclusion, severity, locations, evidence, issue uniqueness, and multi-hit structure before atomically writing the task result, formal issues, hits, and events.

#### Scenario: Valid dimensional result is saved
- **WHEN** all required dimension and quality stages are complete and aggregation submits a valid result
- **THEN** the system writes the formal report in one transaction
- **AND** marks the terminal save successful for that Run

#### Scenario: Quality stages are incomplete
- **WHEN** aggregation attempts final save while a dimension, semantic decision, or required anomaly review is failed, missing, stale, or incomplete
- **THEN** the tool rejects the save
- **AND** leaves all existing formal reports unchanged

#### Scenario: Formal write fails midway
- **WHEN** any task, issue, hit, decision, event, or matching write fails during final save
- **THEN** the complete transaction rolls back
- **AND** the task is not marked completed

### Requirement: Save each review independently of historical issues
The final save service SHALL assign issue numbers for the current report without reading prior formal issues, creating cross-round matches, modifying prior issue status, or computing `persists`, `shifted`, `fixed`, `pending_recheck`, or `uncertain`. Model-supplied historical identity and lifecycle values MUST NOT be trusted or persisted.

#### Scenario: Historical issues exist
- **WHEN** a new review starts while previous reports contain unresolved or resolved issues
- **THEN** no new review Run or tool can read those historical issues
- **AND** the new report is saved solely from current candidates, semantic decisions, and current frozen evidence

#### Scenario: Current report omits an old issue
- **WHEN** the current independent review does not reproduce an issue from an older report
- **THEN** the system does not change the old issue or create a lifecycle event
- **AND** users may compare the two immutable reports manually

## ADDED Requirements

### Requirement: Semantic review tools preserve candidate lineage
The system SHALL provide phase-scoped read and write operations that let a quality Run read frozen candidates and relevant source evidence and persist one terminal semantic decision per candidate without editing scripts or overwriting discovery output.

#### Scenario: Quality Run confirms a candidate
- **WHEN** a quality Run reads a candidate and its trusted source hits and saves a `CONFIRMED` decision
- **THEN** the decision references the candidate, discovery Run, evidence, confidence, and rationale
- **AND** the original candidate remains unchanged
