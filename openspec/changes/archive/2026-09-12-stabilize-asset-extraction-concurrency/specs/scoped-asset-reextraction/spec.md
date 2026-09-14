## ADDED Requirements

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
