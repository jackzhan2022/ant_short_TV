## ADDED Requirements

### Requirement: Scoped Skill catalog
Skill lists SHALL show scope as 系统内置 or 租户级 and display the owning tenant for tenant records. Lists SHALL support combined Agent dropdown and scope filters. The platform catalog SHALL permit viewing tenant records without granting mutation rights.

#### Scenario: Filter catalog
- **WHEN** a viewer selects 分镜规划 and 租户级
- **THEN** only tenant Skill records used by that Agent SHALL be displayed, with the owning tenant name.

### Requirement: Tenant self-service authorization
Only active tenant owners or members holding the active ADMIN role SHALL view and maintain tenant storyboard Skills through the tenant API. Platform permissions alone MUST NOT grant tenant mutation rights.

#### Scenario: Reject ordinary or foreign member
- **WHEN** an ordinary member, removed member, disabled tenant member, or user from another tenant requests tenant Skill data or mutation
- **THEN** access SHALL be denied without reading or changing tenant content.

### Requirement: Restricted tenant overrides
Tenant editors SHALL edit only `short-drama-storyboard-planning` and `short-drama-seedance-video-prompt` using complete Markdown Skill documents. Foundation and material reference Skills SHALL remain read-only. Every override SHALL preserve actor and timestamp metadata and use revision conflict checks. Restoring defaults SHALL remove only the current tenant override.

#### Scenario: Save and restore
- **WHEN** an authorized editor customizes an allowed system Skill and later restores defaults with its current revision
- **THEN** a tenant override SHALL be created on save and removed on restore, leaving the system document and other tenants unchanged.

#### Scenario: Reject stale edit
- **WHEN** an editor saves or restores with an outdated revision
- **THEN** the operation SHALL fail with a conflict and the editor's unsaved content SHALL be preserved.

### Requirement: Tenant-first storyboard execution
Only storyboard Agent runs SHALL prefer the current tenant override of the two allowed codes, falling back to system content when no override exists. Run snapshots SHALL record the selected content, revision and tenant source; a frozen tenant plan MUST NOT run for a different tenant.

#### Scenario: Isolated execution
- **WHEN** tenant A customizes storyboard planning and tenant A, tenant B and a non-storyboard Agent execute
- **THEN** only tenant A's storyboard run SHALL use that override; other runs SHALL use the applicable system Skill.

### Requirement: Preserve storyboard material protocol
Tenant customization MUST NOT change the fixed save schema, material identity validation, rich-text mention nodes, unresolved placeholders, provider reference compilation or tool allowlist.

#### Scenario: Customized storyboard is saved
- **WHEN** a storyboard is generated using tenant Skills and contains bound and unresolved material references
- **THEN** its structured references, rich-text tags and unresolved placeholders SHALL retain the existing representation and validation.
