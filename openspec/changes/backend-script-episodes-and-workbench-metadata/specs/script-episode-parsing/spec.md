## ADDED Requirements

### Requirement: Script workspace exposes parsed episodes
The script workspace endpoint SHALL return an `episodes` collection derived from the current script content. Each episode SHALL include an episode number, a display title, and the episode body content, while preserving all existing workspace fields.

#### Scenario: Parse numbered Chinese episode headings
- **WHEN** the script contains standalone headings such as `第1集` or `第12集：雨夜重逢`
- **THEN** the workspace returns one episode for each heading with the corresponding number, title, and text until the next heading

#### Scenario: Parse Chinese numeral headings
- **WHEN** the script contains standalone headings such as `第一集` or `第十二集 决战`
- **THEN** the workspace returns episodes with numeric episode numbers 1 and 12 and preserves any heading title

#### Scenario: Parse English episode headings
- **WHEN** the script contains standalone headings such as `EP01` or `EP01: Opening`
- **THEN** the workspace returns an episode numbered 1 with the matching display title and body

#### Scenario: Support heading separators and whitespace
- **WHEN** a supported heading uses spaces, a Chinese colon, an English colon, or no title separator
- **THEN** the parser recognizes it as the same episode heading format

### Requirement: Parsed episodes preserve usable content
The parser SHALL assign all script text after an episode heading to that episode until the next recognized heading, and SHALL preserve text before the first heading in the first episode.

#### Scenario: Preserve preamble text
- **WHEN** the script contains a synopsis or metadata before the first episode heading
- **THEN** that text is included in the first returned episode instead of being discarded

#### Scenario: Exclude heading lines from episode body
- **WHEN** an episode heading is recognized
- **THEN** the heading line is used for metadata and is not duplicated in the episode body content

### Requirement: Unstructured scripts have a safe fallback
When no supported episode heading is found, the workspace SHALL return exactly one episode containing the complete non-empty script content.

#### Scenario: Free-form script without episode headings
- **WHEN** the script contains content but no recognized episode heading
- **THEN** the workspace returns one episode numbered 1 and does not create empty placeholder episodes

#### Scenario: Empty or missing script
- **WHEN** the current project has no script or the script content is blank
- **THEN** the workspace returns an empty `episodes` collection
