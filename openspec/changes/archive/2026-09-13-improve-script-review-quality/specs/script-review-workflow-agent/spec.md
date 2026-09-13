## MODIFIED Requirements

### Requirement: Deep review uses child and aggregation Run contracts
A DEEP review SHALL create one candidate-discovery Run for each selected dimension over the frozen review scope, SHALL preserve stable common context for cache reuse, SHALL semantically review every resulting candidate, and SHALL create one final aggregation Run only after all required dimension and quality stages reach terminal success. Dimension Runs SHALL save only candidates and coverage, semantic review SHALL save only auditable decisions, and only the aggregation Run SHALL save the formal report.

#### Scenario: Deep review starts with eight selected dimensions
- **WHEN** the planner freezes a review attempt with eight selected dimensions
- **THEN** the coordinator schedules eight independently tracked dimension Runs using the same frozen model, source, rules, tool contract, and common prompt prefix
- **AND** schedules no aggregation Run until all dimension results and required semantic decisions succeed

#### Scenario: One dimension fails
- **WHEN** seven dimension Runs succeed and the timeline Run fails
- **THEN** the task remains retryable and not completed
- **AND** retry schedules the failed timeline stage without regenerating successful dimensions

## ADDED Requirements

### Requirement: Review Runs preserve a cacheable common prefix
The system SHALL construct a deterministic common prompt prefix from the frozen model, rule revision, tool contract, structure index, and immutable review content, and SHALL place dimension-specific instructions after that prefix. Historical review issues MUST NOT affect the prefix, cache identity, or any new review Run input.

#### Scenario: Consecutive dimensions use unchanged review context
- **WHEN** two dimension Runs belong to the same unchanged review attempt
- **THEN** their common serialized prefix and cache identity are identical
- **AND** only their dimension-specific suffixes differ

#### Scenario: Review rules change
- **WHEN** a new attempt uses a different rule or tool-contract revision
- **THEN** it receives a different cache identity
- **AND** the system does not claim reuse of the earlier semantic context

#### Scenario: Historical reports change
- **WHEN** historical reports or issues are added, edited, or removed while the current frozen review input is unchanged
- **THEN** the common prefix and cache identity remain unchanged
- **AND** no historical issue content is supplied to discovery, semantic quality, anomaly review, or aggregation

### Requirement: Aggregation can retrieve frozen source evidence
The aggregation and quality-review phases SHALL be able to read bounded content from any part of the frozen in-scope review source while retaining tenant, task, version, and scope enforcement.

#### Scenario: Cross-episode candidate requires earlier evidence
- **WHEN** aggregation receives a candidate whose conflicting evidence lies in another review unit
- **THEN** it can retrieve both frozen passages with stable location metadata
- **AND** its decision records both evidence hits
