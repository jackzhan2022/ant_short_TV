# production-task-visibility Specification

## Purpose
Define creator-bound personal visibility and read-only team visibility for owners and active system administrators without bypassing domain resource permissions.
## Requirements
### Requirement: Personal scope is creator-bound within the active team
The system MUST require active membership in the selected team and restrict mine scope to the current creator. List, total, summary, detail, children and task-result metadata SHALL share the same visibility policy. Client-provided creator, scope, type or identifier MUST NOT broaden access.

#### Scenario: Ordinary member supplies another creator
- **WHEN** an ordinary member requests another member's tasks by creator filter or direct task identifier
- **THEN** no other-member task data or counts are returned and an unauthorized direct request is denied

#### Scenario: Cross-team identifier
- **WHEN** a member supplies a task or child identifier belonging to another team
- **THEN** the request is denied regardless of the caller's role in the selected team

### Requirement: Team visibility belongs to owners and system administrators
The system SHALL grant team task visibility only to the current active team OWNER or an active member assigned its enabled system ADMIN role. Ordinary members, project owners, platform roles and custom roles named Admin MUST NOT implicitly obtain this visibility. The frontend SHALL consume a server-derived capability; the backend SHALL independently enforce it.

#### Scenario: Team administrator reads all creators
- **WHEN** a member with the team's active system ADMIN role queries team scope
- **THEN** authorized team-wide task rows, counts and details are returned including former members' retained tasks

#### Scenario: Ordinary member enters team URL
- **WHEN** an ordinary member directly opens team scope or calls its API
- **THEN** the server denies team access even if the client manually displays a team tab

### Requirement: Role changes affect subsequent requests
The system MUST evaluate current membership, ownership and active role assignments on every request instead of trusting cached client capabilities.

#### Scenario: Administrator role is revoked
- **WHEN** a previously authorized administrator loses the ADMIN role
- **THEN** the next team list, count, detail or child request is denied

#### Scenario: Ownership is transferred
- **WHEN** team ownership moves to another active member
- **THEN** the new owner receives team visibility and the previous owner retains it only if independently qualified as an active system administrator

### Requirement: Other-member task access in the center is read-only
The center MUST deny cancellation, retry and regeneration of other members' tasks even for owners and administrators. Own-task control SHALL additionally satisfy current business authorization and lifecycle checks. This requirement governs the new task-center surface and SHALL NOT silently rewrite existing business endpoint policies.

#### Scenario: Owner tries to cancel another member's task
- **WHEN** an owner directly submits a center cancellation request for another creator's task
- **THEN** the request is denied with no domain, execution or settlement mutation

#### Scenario: Administrator operates own task
- **WHEN** an administrator controls their own task
- **THEN** the action is permitted only if its domain permission and current lifecycle allow it

### Requirement: Task visibility does not bypass resource privacy
Task responses MUST exclude raw prompts, full script/media payloads, provider cost details, internal request logs and unsanitized errors. Domain destinations and result access SHALL independently enforce resource permissions. A creator who has lost project access SHALL receive only a restricted own-task summary with type, status and timestamps. Unbound tasks SHALL follow the same creator/team-role policy.

#### Scenario: Unbound private task belongs to a colleague
- **WHEN** an ordinary member requests another creator's unbound task
- **THEN** the server denies the request despite both users belonging to the same team

#### Scenario: Creator loses project access
- **WHEN** a creator queries their task after project access is revoked
- **THEN** the response omits project title, input, results and detailed errors and exposes no control or result-opening action
