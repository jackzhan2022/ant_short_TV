## MODIFIED Requirements

### Requirement: Execute the four analysis stages in order
The system SHALL run global understanding followed by episode splitting through Workflow Agents. After formal splitting succeeds, summary and asset-recognition work SHALL be persisted and independently progressed per episode using trusted current input. The system SHALL have no legacy executor, LEGACY_V1 mode or routing switch. Per-episode asset completion and coverage validation SHALL durably trigger automatic storyboard generation with idempotency and manual-result protection.

#### Scenario: Advance after committed formal output
- **WHEN** splitting completes its terminal save and formal coverage validation
- **THEN** the system persists summary and recognition work for the frozen episode set
- **AND** neither branch depends on the generated output of the other

#### Scenario: Preserve failed stage
- **WHEN** an episode summary fails
- **THEN** it remains failed with an actionable error while recognition continues
- **AND** earlier committed data remains available and the analysis is not falsely completed

#### Scenario: Restart during downstream work
- **WHEN** the service restarts with summary, recognition or automatic storyboard work pending
- **THEN** it resumes from persisted claims and outcomes without duplicate generation or point reservation

#### Scenario: Recognition commits one episode
- **WHEN** asset persistence and coverage succeed for an episode
- **THEN** a durable event schedules that episode's storyboard without waiting for whole-script summaries
- **AND** existing manual storyboards are protected and storyboard failure does not undo recognition success
