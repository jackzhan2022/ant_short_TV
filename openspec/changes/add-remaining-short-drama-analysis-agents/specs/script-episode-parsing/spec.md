## MODIFIED Requirements

### Requirement: Script workspace exposes parsed episodes
The script workspace endpoint SHALL return the current active formal `script_episode` collection produced by successful intelligent splitting. Each episode SHALL include its stable ID, episode number, display title, and exact body content while preserving existing workspace fields.

#### Scenario: Successful AI split exists
- **WHEN** the current script has a valid committed intelligent split
- **THEN** the workspace returns those active stable formal episodes in episode order
- **AND** does not reparse headings into a competing episode identity set

#### Scenario: Intelligent splitting is pending or failed
- **WHEN** no current valid formal split exists
- **THEN** the workspace exposes the splitting status and any non-authoritative preview separately
- **AND** does not represent a deterministic one-episode fallback as a successful formal AI split

### Requirement: Parsed episodes preserve usable content
The split save tool SHALL assign every non-whitespace character of the current script snapshot to exactly one ordered formal episode and SHALL preserve the exact source text selected by validated boundaries.

#### Scenario: Preserve preamble text
- **WHEN** the script contains synopsis or metadata before the first explicit episode heading
- **THEN** valid AI boundaries include that text in one episode rather than discard it

#### Scenario: Preserve source formatting
- **WHEN** a boundary-delimited episode is saved
- **THEN** its formal content preserves the source characters and formatting
- **AND** the model cannot replace the body with a rewrite

## REMOVED Requirements

### Requirement: Unstructured scripts have a safe fallback
**Reason**: A one-episode preview may remain useful while analysis is unavailable, but silently treating it as a valid formal split conflicts with the always-AI, full-coverage save contract.

**Migration**: Keep any deterministic one-episode representation as explicitly non-authoritative workspace preview data; only `save_episode_splitting` may establish the current formal episode set.

