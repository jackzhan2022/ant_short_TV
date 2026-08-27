# script-episode-parsing Specification

## Purpose

TBD - created by archiving an earlier change. Update Purpose after archive.
## Requirements
### Requirement: Script workspace exposes parsed episodes
The script workspace endpoint SHALL return an `episodes` collection derived from the current script content or the latest valid intelligent analysis for the current script version. Each episode SHALL include an episode number, a display title, and the episode body content, while preserving all existing workspace fields.

#### Scenario: Parse numbered Chinese episode headings
- **WHEN** the script contains standalone headings such as `第1集` or `第12集：雨夜重逢`
- **THEN** the workspace returns one episode for each heading with the corresponding number, title, and text until the next heading

#### Scenario: Use intelligent splitting for unstructured content
- **WHEN** the script has no reliable episode headings and a valid intelligent split result exists for the current version
- **THEN** the workspace returns the AI-generated episode boundaries and content
- **AND** preserves the analysis result for review

#### Scenario: Fall back while intelligent splitting is pending
- **WHEN** the script has no reliable episode headings and intelligent splitting is pending or failed
- **THEN** the workspace returns the deterministic one-episode fallback
- **AND** exposes the intelligent splitting status separately

### Requirement: Parsed episodes preserve usable content
The parser SHALL assign all script text after an episode heading to that episode until the next recognized heading, and SHALL preserve text before the first heading in the first episode.

#### Scenario: Preserve preamble text
- **WHEN** the script contains a synopsis or metadata before the first episode heading
- **THEN** that text is included in the first returned episode instead of being discarded

#### Scenario: Exclude heading lines from episode body
- **WHEN** an episode heading is recognized
- **THEN** the heading line is used for metadata and is not duplicated in the episode body content

### Requirement: Unstructured scripts have a safe fallback
When no supported episode heading or valid intelligent split result is available, the workspace SHALL return exactly one episode containing the complete non-empty script content.

#### Scenario: Free-form script without episode headings
- **WHEN** the script contains content but no recognized episode heading and no valid intelligent split result
- **THEN** the workspace returns one episode numbered 1
- **AND** does not create empty placeholder episodes

#### Scenario: Empty or missing script
- **WHEN** the current project has no script or the script content is blank
- **THEN** the workspace returns an empty `episodes` collection
