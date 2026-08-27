## ADDED Requirements

### Requirement: Direct video screenplay uses one billable execution stage

For a new video decomposition episode that produces a valid direct screenplay, the system SHALL create, settle, and expose one video-understanding execution stage only. It SHALL not create a text draft-generation execution, reservation, usage record, or provider call for that episode.

#### Scenario: Direct screenplay analysis succeeds

- **WHEN** a new video decomposition episode completes video understanding with a valid `script`
- **THEN** the video-understanding execution is settled as successful and no `DRAFT_GENERATION` execution attempt or `video_script_draft` call log exists for that episode

#### Scenario: Historical draft-generation task runs after deployment

- **WHEN** an episode already in `PENDING_DRAFT` is processed after deployment
- **THEN** its legacy draft-generation execution and settlement behavior remain available
