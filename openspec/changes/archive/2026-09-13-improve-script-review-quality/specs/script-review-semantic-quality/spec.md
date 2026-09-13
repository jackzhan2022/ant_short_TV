## ADDED Requirements

### Requirement: Every discovered candidate receives an auditable semantic decision
The system SHALL evaluate every mechanically parseable review candidate for evidence support, dimension applicability, plausible alternative explanation, severity calibration, and suggestion effectiveness, and SHALL persist both the original candidate and a semantic status of `CONFIRMED`, `NEEDS_HUMAN_REVIEW`, `REJECTED`, or `INSUFFICIENT_EVIDENCE`.

#### Scenario: Evidence supports the reported contradiction
- **WHEN** semantic review confirms that the cited passages establish a contradiction under the selected dimension
- **THEN** the candidate is marked `CONFIRMED` with confidence and a concise decision rationale
- **AND** the original candidate and evidence remain auditable

#### Scenario: Candidate has a plausible competing interpretation
- **WHEN** the cited text permits materially different reasonable interpretations
- **THEN** the candidate is marked `NEEDS_HUMAN_REVIEW`
- **AND** the system does not silently reject or promote it as certain

### Requirement: Invalid and rejected candidates remain observable
The system MUST NOT silently discard a discovered candidate because of schema, location, evidence, or semantic validation failure. It SHALL either request a bounded correction or persist the candidate with its normalized rejection reason and source Run.

#### Scenario: Candidate cites an invalid offset
- **WHEN** a dimension Run submits a candidate whose offset cannot be verified
- **THEN** the system records or returns the precise validation failure for bounded correction
- **AND** operators can determine that the model produced a candidate that did not reach the formal report

### Requirement: Zero-problem output requires anomaly review
The system SHALL run a quality review before completing a report when every selected dimension returns no candidates or when current-run coverage falls below the configured anomaly threshold. Historical issue content or volume MUST NOT participate in the decision.

#### Scenario: All dimensions return no candidates
- **WHEN** every required dimension Run completes with an empty candidate set
- **THEN** the task enters quality review instead of immediately receiving an unconditional 100 score
- **AND** the quality review can read current-run coverage and relevant frozen source text but cannot read history

### Requirement: Human benchmarks measure review quality
The system SHALL support version-bound human benchmark issues and SHALL report candidate recall, formal-report recall, false-positive rate, history-isolation accuracy, and per-dimension results without treating model scores as ground truth.

#### Scenario: Evaluate against a human issue set
- **WHEN** an authorized evaluation runs against a script version with curated human issues
- **THEN** the system matches model findings within configured dimension and evidence tolerances
- **AND** reports missed, confirmed, disputed, and extra findings per dimension
