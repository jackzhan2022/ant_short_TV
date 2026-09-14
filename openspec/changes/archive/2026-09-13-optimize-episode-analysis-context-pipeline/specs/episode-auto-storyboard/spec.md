## ADDED Requirements

### Requirement: Persist automatic storyboard dependency
The system SHALL durably schedule one episode storyboard after that episode's asset save and coverage validation succeed, independently of summaries and other episodes, using existing authorization and billing.

#### Scenario: One episode completes recognition
- **WHEN** its current-source asset transaction and coverage succeed
- **THEN** a durable trigger is recorded and its storyboard becomes eligible without waiting for the script-level finalizer

#### Scenario: Restart between save and dispatch
- **WHEN** the worker restarts after asset completion
- **THEN** recovery delivers the persisted trigger without losing or duplicating the storyboard

### Requirement: Protect generated and manual work
The system SHALL enforce source-version scoped idempotency across automatic dispatch and competing manual generation, preserve edited or confirmed storyboards, and reject stale triggers.

#### Scenario: Duplicate callback or manual race
- **WHEN** completion is delivered twice or manual generation competes for the same episode source
- **THEN** at most one automatic task is admitted and existing work is reused or explicitly protected

#### Scenario: Content changes or storyboard is edited
- **WHEN** an old event encounters changed source or protected storyboard work
- **THEN** it records a stale or protected outcome without overwriting that work

### Requirement: Isolate storyboard failures and expose outcomes
The system SHALL expose separate per-episode storyboard status, SHALL support authorized independent retry, and SHALL preserve completed recognition when storyboard generation fails or lacks funds.

#### Scenario: Storyboard fails
- **WHEN** the storyboard request times out or cannot reserve points
- **THEN** recognition remains successful, a specific failed or blocked storyboard state is shown, and duplicate billing is prevented
