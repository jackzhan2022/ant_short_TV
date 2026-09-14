## MODIFIED Requirements

### Requirement: Task visibility does not bypass resource privacy
Task list, summary and lightweight state responses MUST exclude full prompts, full script/media payloads and raw internal records. Detail content SHALL expose saved user-facing prompts, source content, reference media and results only after both task visibility and the corresponding current domain content-read permissions are satisfied. All responses MUST exclude credentials, internal system prompts, provider cost details, internal request logs and unsanitized errors. Domain destinations, content sections, text continuation, media previews, thumbnails and downloads SHALL independently enforce resource permissions and verify tenant, task and version ownership. A creator who has lost project access SHALL receive only a restricted own-task summary with type, status and timestamps. Unbound tasks SHALL follow the same creator/team-role policy and their source-specific content authorization. Team visibility and shared execution MUST NOT implicitly grant access to another creator's private inputs. Copy requires content-read permission; downloads additionally require the existing domain download permission where applicable.

#### Scenario: Unbound private task belongs to a colleague
- **WHEN** an ordinary member requests another creator's unbound task
- **THEN** the server denies the request despite both users belonging to the same team

#### Scenario: Creator loses project access
- **WHEN** a creator queries their task after project access is revoked
- **THEN** the response omits project title, input, results and detailed errors and exposes no control or result-opening action
- **AND** subsequent section, continuation and media requests cannot recover the hidden content

#### Scenario: Owner can view a task but not its source draft
- **WHEN** a team owner opens a colleague's task without the required source-draft read permission
- **THEN** the task remains visible according to the team policy but the protected draft and prompt content are restricted without leaking their resource references

#### Scenario: Authorized user reads the submitted prompt
- **WHEN** a caller can view the task and passes its domain content-read checks
- **THEN** the detail returns the saved user-facing prompt and authorized reference/result content
- **AND** the task list still excludes those full contents

#### Scenario: Caller replaces a content resource identifier
- **WHEN** a caller requests a media or text resource belonging to another task, tenant or unauthorized execution version
- **THEN** the server denies access even when the caller can view the task named in the URL

#### Scenario: Membership or role is revoked during detail viewing
- **WHEN** a previously authorized reader loses required membership, team role or domain content access
- **THEN** the next content or resource request rechecks that access and is denied or restricted
- **AND** the client clears content that the response indicates is no longer authorized

#### Scenario: Reader has preview but no download permission
- **WHEN** a task reader passes media preview authorization but lacks the domain's required download permission
- **THEN** the center exposes preview without offering or authorizing its download action
