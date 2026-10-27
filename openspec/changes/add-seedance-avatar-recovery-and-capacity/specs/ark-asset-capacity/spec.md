## ADDED Requirements

### Requirement: Enforce shared account asset and group quotas
The system SHALL account for remote resources across the configured complete project list and local history, including AIGC and LivenessFace external resources, and SHALL enforce limits of 50 assets and 50 groups including in-flight creation reservations. Manual ingestion and automatic recovery MUST use the same admission control.

#### Scenario: Concurrent creation near capacity
- **WHEN** two workers attempt ingestion while only one slot remains
- **THEN** at most one creation is admitted and the other waits without exceeding 50

#### Scenario: Inventory cannot be trusted
- **WHEN** inventory requests fail or a create outcome is unknown
- **THEN** the system does not interpret missing information as free capacity and retains uncertain reservations

### Requirement: Reclaim safe managed resources to preserve headroom
The system SHALL check inventory every five minutes and before admission, and SHOULD reclaim from 45 toward 40 resources when safe. It SHALL delete only proven system-managed resources without active consumers, prioritizing obsolete resources and then least recently used resources. No original business media SHALL be deleted.

#### Scenario: High water mark with safe candidates
- **WHEN** inventory contains 45 assets including five old managed assets without consumers
- **THEN** the cleanup attempts to remove those assets and reach 40 only after deletion is confirmed

#### Scenario: All resources are protected or external
- **WHEN** insufficient free capacity remains and all candidates are in use or externally owned
- **THEN** new ingestion waits with a capacity reason and no protected resource is deleted

### Requirement: Protect in-flight references and invalidate evicted bindings
The system SHALL serialize consumer registration against deletion markers and SHALL protect queued, submitting, generating and uncertain provider operations. Confirmed deletion SHALL invalidate matching current avatar bindings and mark the managed identity evicted; subsequent use SHALL recreate it from the preserved source.

#### Scenario: Cleanup races with video creation
- **WHEN** a video task registers a reference while cleanup tries to claim the same asset
- **THEN** either consumer registration wins and cleanup skips it, or deletion wins and task creation cannot use the stale identity

#### Scenario: Deletion is not confirmed
- **WHEN** a remote delete times out or fails
- **THEN** quota occupancy and the deletion audit remain until reconciliation confirms the outcome

### Requirement: Bound provider creation and remove only empty groups
The system SHALL share a CreateAsset limiter across workers and SHALL reuse managed AIGC groups. A group SHALL be reclaimed only after verifying that it is empty and has no in-flight creation.

#### Scenario: A group is still occupied
- **WHEN** a managed group contains an external asset or has an admitted creation
- **THEN** the group is not deleted even if its local last-used time is old

### Requirement: Admit the complete storyboard without partial-allocation deadlocks
The system SHALL check the missing portrait reference count before uploading the first image, SHALL serialize admitted recovery preparation at account scope, and SHALL defer other ingestion until the admitted preparation succeeds or terminates.

#### Scenario: Two storyboards need nine slots with only ten available
- **WHEN** both request recovery concurrently
- **THEN** one storyboard prepares all its images first and the other waits without pinning a partial set

#### Scenario: The full storyboard cannot fit
- **WHEN** only six safe slots exist and nine images require ingestion
- **THEN** recovery waits for capacity before creating any of those assets
