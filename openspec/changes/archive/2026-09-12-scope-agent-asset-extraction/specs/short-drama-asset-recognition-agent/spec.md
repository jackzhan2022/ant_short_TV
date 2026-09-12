## ADDED Requirements

### Requirement: Scope formal recognition to selected asset types
The per-episode asset-recognition Agent and `save_episode_assets` tool SHALL accept `ALL`, `CHARACTER`, `SCENE`, or `PROP` scope. A non-`ALL` Run MUST read, emit, validate, and persist only its selected canonical asset type and its owned visual variants.

#### Scenario: Character-only Agent Run
- **WHEN** a child Agent Run starts with `CHARACTER` scope
- **THEN** it recognizes characters and character looks only
- **AND** the formal write tool rejects scene or prop payload collections

#### Scenario: All-type Agent Run
- **WHEN** a child Agent Run starts with `ALL` scope
- **THEN** it retains the existing five-category recognition behavior

### Requirement: Finalize only the selected recognition scope
The recognition finalizer SHALL retire or replace Agent-managed bindings, variants, and unreferenced assets only for the operation scope and only after every episode in the frozen scoped snapshot succeeds.

#### Scenario: Scene-only run succeeds
- **WHEN** all child Runs for a `SCENE` operation succeed
- **THEN** the finalizer may retire stale AI-managed scene bindings, scene variants, and unreferenced scenes for that snapshot
- **AND** it does not retire character, character-look, prop, or prop-state data

#### Scenario: Scoped run has a failed episode
- **WHEN** any child Run in a scoped operation fails
- **THEN** successful scoped episode writes remain available
- **AND** the finalizer does not retire prior data for that scope
