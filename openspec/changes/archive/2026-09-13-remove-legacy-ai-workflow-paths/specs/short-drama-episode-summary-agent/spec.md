## ADDED Requirements

### Requirement: Read and write summaries through the formal repository only
Summary saves, edits, episode navigation and Agent tools SHALL use script_episode_summary as the sole persisted summary source. The system SHALL remove the script_episode.summary mirror column, dual writes and fallback reads.

#### Scenario: Edit a formal summary
- **WHEN** an authorized user updates summary, highlights or endingHook
- **THEN** all current consumers obtain the updated formal document without a mirrored legacy value

#### Scenario: Episode has no formal summary
- **WHEN** an episode has no current formal summary
- **THEN** consumers expose a missing summary state instead of reading legacy analysis JSON or a removed column

## REMOVED Requirements

### Requirement: Keep legacy summary reads compatible during migration
**Reason**: The application has no external clients requiring the legacy mirror.
**Migration**: Move every summary consumer to the formal repository and drop the mirror after the authorized historical cleanup.
