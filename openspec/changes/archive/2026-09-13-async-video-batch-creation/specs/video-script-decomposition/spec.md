## MODIFIED Requirements

### Requirement: Create a video script decomposition batch
The system SHALL allow an authorized project user to create a decomposition batch containing one or more uploaded videos, where each video represents exactly one episode. A successful creation response SHALL mean that the batch, ordered pending episodes, and initial pending attempts are durably stored; it SHALL NOT wait for AI execution creation, billing resolution, point reservation, or provider contact.

#### Scenario: Create a batch from multiple videos
- **WHEN** the user submits an ordered list of valid uploaded videos
- **THEN** the system atomically creates one batch and one pending episode task per video using the submitted order
- **AND** the system returns the batch identifier, episode numbers, filenames, and initial statuses without synchronously initializing billable AI executions

#### Scenario: Reject an invalid video before task creation
- **WHEN** a submitted video is missing, unsupported, too large, or exceeds the configured duration limit
- **THEN** the system rejects that video with a field-level error
- **AND** no batch, episode, or executable AI task is created for the rejected submission

#### Scenario: Defer an execution initialization failure
- **WHEN** the batch and episodes are valid but a claimed episode later cannot initialize billing or reserve points
- **THEN** the already-created batch remains available
- **AND** the system records the failure on the affected episode without rolling back sibling episodes
