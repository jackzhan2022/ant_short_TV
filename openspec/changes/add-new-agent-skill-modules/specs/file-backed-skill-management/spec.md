## ADDED Requirements

### Requirement: Project-deployed SKILL.md storage
The system SHALL store each Skill（新）as exactly one UTF-8 file at `<configured-skill-root>/<skill-code>/SKILL.md`, where the configured Skill root belongs to or is mounted into the deployed backend project. The file MUST contain YAML frontmatter with `name` and `description` followed by Markdown instructions, and the immutable Skill code MUST map to exactly one safe directory name.

#### Scenario: Valid Skill is created
- **WHEN** an authorized editor creates a Skill with a unique safe code and valid complete SKILL.md content
- **THEN** the system SHALL atomically create the Skill directory and file and return the parsed metadata and content

#### Scenario: Unsafe Skill code is submitted
- **WHEN** a requested Skill code contains traversal, separators, an absolute path, unsupported characters, or does not resolve directly beneath the configured root
- **THEN** the system SHALL reject the request without reading or writing outside the configured Skill root

#### Scenario: Invalid frontmatter is saved
- **WHEN** SKILL.md content has malformed YAML or lacks the required name or description
- **THEN** the system SHALL return validation details and keep the prior file unchanged

### Requirement: Immediate Skill editing
An authorized editor SHALL be able to edit the complete SKILL.md content, including its frontmatter and Markdown body. A successful save SHALL use atomic file replacement and SHALL become visible to all subsequent Agent constructions immediately, without a publish operation, Skill version, or application restart.

#### Scenario: Referenced Skill is edited
- **WHEN** an editor successfully saves changes to a Skill referenced by one or more Agents
- **THEN** the next run of every referencing Agent SHALL load the new complete Skill content

#### Scenario: Concurrent edit uses stale state
- **WHEN** an editor attempts to overwrite a Skill whose content has changed since the editor loaded it
- **THEN** the system SHALL reject the stale write with a conflict and preserve the newer file

### Requirement: Skill discovery, copying, and reference protection
The Skill（新）module SHALL support listing, searching, viewing, copying, and deleting file-backed Skills. Detail and list responses SHALL identify referencing workflow Agents. A copied Skill MUST use a new unique immutable code. A referenced Skill MUST NOT be deleted.

#### Scenario: User inspects Skill references
- **WHEN** an authorized viewer opens a Skill detail
- **THEN** the system SHALL show the current parsed metadata, full editable content, content revision token, and referencing Agents

#### Scenario: Referenced Skill deletion is attempted
- **WHEN** an authorized editor attempts to delete a Skill associated with any workflow Agent
- **THEN** the system SHALL reject deletion and return the referencing Agents

#### Scenario: Unreferenced Skill is copied
- **WHEN** an authorized editor copies a Skill using a new valid unique code
- **THEN** the system SHALL atomically create an independent SKILL.md with equivalent content and no Agent associations

### Requirement: Skill permissions and filesystem failure behavior
The system SHALL enforce independent Skill（新）view and edit permissions. Filesystem read, validation, or write failures MUST be normalized into safe application errors and MUST NOT expose server paths or partial file contents to unauthorized users.

#### Scenario: View-only user opens Skill（新）
- **WHEN** a user has Skill（新）view permission but not edit permission
- **THEN** the system SHALL allow listing and reading Skills while disabling create, save, copy, and delete operations

#### Scenario: Atomic replacement fails
- **WHEN** the server cannot complete a Skill file replacement
- **THEN** the system SHALL report a normalized failure and SHALL retain either the complete prior file or the complete new file, never a partially written SKILL.md
