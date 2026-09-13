## ADDED Requirements

### Requirement: Agent reads the complete authorized project script
The system SHALL expose a read-only workflow tool named `read_project_full_script`. The tool MUST accept an empty input object and MUST read only the project resolved from the workflow execution context.

#### Scenario: Agent reads all valid episodes
- **WHEN** an Agent authorized for `read_project_full_script` invokes it in a project-scoped run
- **THEN** the system returns the current content for every valid episode in that project

#### Scenario: Agent cannot override the project scope
- **WHEN** an Agent includes a project identifier or another extra field in the tool arguments
- **THEN** the system rejects the arguments and does not read another project's script

### Requirement: Complete script results preserve episode boundaries
The `read_project_full_script` result SHALL contain an `episodes` array ordered by ascending episode number. Every array item MUST contain `episodeId`, `episodeNo`, and `content`, and MUST expose the episode title, summary, and status when available.

#### Scenario: Project has multiple episodes
- **WHEN** the authorized project has multiple valid episodes with current script versions
- **THEN** the result contains one separately identified record per episode in ascending episode order

#### Scenario: Project has no valid episodes
- **WHEN** the authorized project has no valid episodes
- **THEN** the result contains an empty `episodes` array
