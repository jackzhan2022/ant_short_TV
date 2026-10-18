## ADDED Requirements

### Requirement: Scoped Skill catalog
Skill lists SHALL show scope as 系统内置 or 租户级 and display the owning tenant for tenant records. Lists SHALL support combined Agent dropdown and scope filters. The platform catalog SHALL permit viewing tenant records without granting mutation rights.

#### Scenario: Filter catalog
- **WHEN** a viewer selects 分镜规划 and 租户级
- **THEN** only tenant Skill records used by that Agent SHALL be displayed, with the owning tenant name.

### Requirement: Tenant self-service authorization
Active tenant members SHALL view only saved Skills belonging to their current tenant. Only owners or members holding the active ADMIN role SHALL create, edit or remove those records. Platform permissions alone MUST NOT grant tenant mutation rights. Tenant list/detail APIs MUST NOT expose system Skill content or fall back to system documents.

#### Scenario: Ordinary member can read but cannot mutate
- **WHEN** an active ordinary member opens the tenant Skill list
- **THEN** only their tenant's saved records SHALL be returned, with no editing controls or mutation permission.

#### Scenario: Reject foreign or inactive member
- **WHEN** a removed member, disabled tenant member, or user from another tenant requests tenant Skill data or mutation
- **THEN** access SHALL be denied without reading or changing tenant content.

#### Scenario: No system fallback in tenant APIs
- **WHEN** a tenant has no saved Skill of an allowed type
- **THEN** its list SHALL omit that type and its detail SHALL return 404 instead of system content; tenant managers SHALL be able to add a blank supplement of that type.

### Requirement: Restricted tenant overrides
Tenant editors SHALL edit only `short-drama-storyboard-planning` and `short-drama-seedance-video-prompt` using complete Markdown Skill documents. Foundation and material reference Skills SHALL remain read-only. Every override SHALL preserve actor and timestamp metadata and use revision conflict checks. Restoring defaults SHALL remove only the current tenant override.

#### Scenario: Save and restore
- **WHEN** an authorized editor customizes an allowed system Skill and later restores defaults with its current revision
- **THEN** a tenant override SHALL be created on save and removed on restore, leaving the system document and other tenants unchanged.

#### Scenario: Reject stale edit
- **WHEN** an editor saves or restores with an outdated revision
- **THEN** the operation SHALL fail with a conflict and the editor's unsaved content SHALL be preserved.

### Requirement: Tenant-first storyboard execution
Storyboard runs SHALL load all associated system Skills at priority 100 and then the current tenant's saved Skills of the two allowed codes at priority 200. Both layers MUST coexist in the model instructions and snapshots. Conflicting creative instructions SHALL follow the tenant layer; non-conflicting system instructions SHALL remain effective. A frozen tenant plan MUST NOT run for a different tenant.

#### Scenario: Isolated execution
- **WHEN** tenant A customizes storyboard planning and tenant A, tenant B and a non-storyboard Agent execute
- **THEN** tenant A's storyboard run SHALL include both system and tenant instructions, with tenant instructions explicitly taking precedence; tenant B and non-storyboard runs SHALL use only their applicable system instructions.

### Requirement: Preserve storyboard material protocol
Tenant customization MUST NOT change the fixed save schema, material identity validation, rich-text mention nodes, unresolved placeholders, provider reference compilation or tool allowlist.

#### Scenario: Customized storyboard is saved
- **WHEN** a storyboard is generated using tenant Skills and contains bound and unresolved material references
- **THEN** its structured references, rich-text tags and unresolved placeholders SHALL retain the existing representation and validation.
