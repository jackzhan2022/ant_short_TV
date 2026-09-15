# scoped-asset-reextraction Specification

## Purpose
TBD - created by archiving change scope-agent-asset-extraction. Update Purpose after archive.
## Requirements
### Requirement: Start scoped formal asset re-extraction from asset settings
The system SHALL let an authorized asset-settings user submit formal asset re-extraction for `ALL`, `CHARACTER`, `SCENE`, or `PROP`, using the per-episode asset-recognition Agent and formal asset-write tool rather than the legacy raw JSON element-extraction path.

#### Scenario: Re-extract only scenes
- **WHEN** a user confirms `SCENE` re-extraction from asset settings
- **THEN** the system creates an asynchronous scoped recognition operation for the frozen active episodes
- **AND** each child Run invokes the formal Agent with `SCENE` scope
- **AND** the asset-settings client does not call `scripts/ai-extract-elements`

#### Scenario: Re-extract all asset types
- **WHEN** a user submits `ALL` re-extraction
- **THEN** the system recognizes characters, character looks, scenes, props, and prop states for every frozen active episode
- **AND** the operation exposes persisted progress and terminal status

### Requirement: Preflight existing scoped asset impact before submission
The system SHALL provide a preflight result for a selected scope that reports existing canonical assets, visual variants, and non-empty prompts that can be affected by scoped re-extraction.

#### Scenario: Scope has no existing content
- **WHEN** a user requests preflight for a scope with no existing canonical assets, variants, or non-empty prompts
- **THEN** the client submits re-extraction directly with the default `FILL_EMPTY` prompt policy

#### Scenario: Scope has existing content
- **WHEN** a user requests preflight for a scope containing existing assets, variants, or non-empty prompts
- **THEN** the client presents the reported counts and requires explicit confirmation before submitting the operation

### Requirement: Require explicit prompt regeneration policy
The confirmation interface SHALL default to `FILL_EMPTY` and SHALL offer `REGENERATE_ALL` only as an explicit user selection for the submitted scope.

#### Scenario: Confirm only missing prompts
- **WHEN** a user confirms re-extraction without changing the default prompt policy
- **THEN** the operation generates prompts only for new or empty canonical assets and variants in the selected scope
- **AND** existing non-empty prompts remain unchanged

#### Scenario: Confirm prompt regeneration
- **WHEN** a user explicitly selects `REGENERATE_ALL` and confirms
- **THEN** the operation regenerates prompts only for the selected scope
- **AND** the confirmation interface reports the number of existing non-empty prompts that can be replaced

### Requirement: Surface scoped operation failures
The asset-settings interface SHALL display the terminal failure reason returned by the scoped operation and SHALL clear its loading state on every terminal status.

#### Scenario: Agent tool validation fails
- **WHEN** a scoped child Run fails validation
- **THEN** the parent operation reaches a failed terminal status with an actionable reason
- **AND** the asset-settings interface displays that reason rather than only a generic extraction failure

### Requirement: Present reused and conflicting extraction tasks
The asset-settings interface SHALL attach to an equivalent active task returned by submission and SHALL show a conflicting task reference for incompatible requests. It SHALL preserve preflight confirmation, selected ALL / CHARACTER / SCENE / PROP scope and FILL_EMPTY / REGENERATE_ALL policy without silently substituting another request.

#### Scenario: Equivalent task already exists
- **WHEN** the user submits an equivalent request while a task is active
- **THEN** the interface follows the existing execution progress without creating a second task

#### Scenario: Different request conflicts
- **WHEN** the user submits a different scope or policy while the script is owned
- **THEN** the interface explains the active conflict and allows viewing authorized task progress without automatic resubmission or overwrite

#### Scenario: Existing task reaches a terminal state
- **WHEN** the followed task succeeds, fails or is canceled
- **THEN** the loading state clears and the terminal result or actionable failure is displayed

### Requirement: Execute scoped episode units with bounded concurrency
The system SHALL execute independent episode units in a scoped asset re-extraction concurrently up to the configured per-operation workflow fan-out limit. It MUST NOT hold a database transaction open during an Agent or provider call.

#### Scenario: Re-extract a multi-episode script
- **WHEN** an authorized user starts scoped re-extraction for more than one frozen active episode
- **THEN** the system may run multiple episode Agent children concurrently
- **AND** active children for that operation do not exceed the configured workflow fan-out limit

#### Scenario: Configured concurrency exceeds remaining work
- **WHEN** fewer runnable episode units remain than the configured concurrency
- **THEN** the system starts no more workers than runnable units
- **AND** does not create duplicate child Runs for the same unit attempt

### Requirement: Isolate concurrent unit outcomes and preserve recovery
The system SHALL persist each concurrent unit claim and outcome independently, retain successful formal commits, and make only pending, failed, or interrupted units runnable after retry or execution recovery.

#### Scenario: One concurrent child fails
- **WHEN** one child Run fails while peer children succeed
- **THEN** the failed unit records its actionable error without reverting successful peer units
- **AND** the parent operation fails only after submitted peer work has settled

#### Scenario: Worker loses execution ownership
- **WHEN** the active execution claim is lost while concurrent children are running
- **THEN** the worker stops scheduling useful new work and does not finalize the snapshot
- **AND** late writes from the stale attempt are rejected by ownership fencing
- **AND** a replacement attempt reuses committed units and schedules only unfinished units

#### Scenario: User cancels concurrent re-extraction
- **WHEN** cancellation is requested while episode children are pending or running
- **THEN** the system stops scheduling useful new children and cancels the parent operation
- **AND** it does not retire old assets or discard already committed formal results

### Requirement: Finalize concurrent re-extraction only after complete coverage
The system SHALL finalize a scoped re-extraction only after every frozen episode unit has a successful formal save under the current source snapshot and owning execution attempt.

#### Scenario: All concurrent units succeed
- **WHEN** every frozen episode unit has successful commit evidence after concurrent execution settles
- **THEN** the system performs scoped retirement exactly once
- **AND** marks the snapshot, operation, and unified execution successful

#### Scenario: Any unit remains incomplete
- **WHEN** any frozen unit is failed, pending, interrupted, or missing formal commit evidence
- **THEN** the system does not retire old assets
- **AND** exposes persisted completed and failed counts for a targeted retry

