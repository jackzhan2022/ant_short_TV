## ADDED Requirements

### Requirement: Automatic full inventory normalization
The system SHALL analyze every active character, scene and prop in the script and automatically publish validated decisions without a human review queue.

#### Scenario: Aliases and visual states
- **WHEN** evidence establishes mother/mom as one character or whole/torn orders as one prop
- **THEN** the identities merge while visual variant IDs and images remain available

#### Scenario: Mixed occurrence ownership
- **WHEN** one generic prop identity contains different owners in different episodes
- **THEN** explicit binding assignments split those occurrences without moving unrelated episodes

### Requirement: Durable asynchronous execution
The system SHALL expose normalization through the existing durable execution queue and run once at the end of each extraction batch.

#### Scenario: Retry after commit
- **WHEN** an execution retries after normalization has committed
- **THEN** it reuses its committed result without repeating model inference or merging again

### Requirement: Atomic validated publication
The system MUST reject missing coverage, cross-type merges, conflicting assignments, invented IDs, unverifiable evidence and stale inputs before publishing any changes.

#### Scenario: Stale script or lost claim
- **WHEN** the script, inventory or execution claim changes during inference
- **THEN** publication is rejected and no asset is modified by the stale attempt

### Requirement: Preserve references and gate downstream work
The system SHALL retain original identity redirects and images and SHALL block editing and generation until required normalization succeeds, while keeping asset lists readable.

#### Scenario: Pending normalization
- **WHEN** extraction or normalization is incomplete or failed
- **THEN** automatic storyboard dispatch waits and user sees processing status with retry access
