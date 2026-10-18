## 1. Tenant storage and authorization

- [x] 1.1 Add tenant override schema, metadata, optimistic update and reset.
- [x] 1.2 Add owner/admin-only tenant API and read-only platform catalog with scope/Agent filters.

## 2. Runtime resolution

- [x] 2.1 Resolve only the two allowed overrides for storyboard runs and record tenant provenance in frozen plans/snapshots.
- [x] 2.2 Verify tenant isolation, non-storyboard fallback and preserved material/rich-text protocol.

## 3. Management UI

- [x] 3.1 Add Agent/scope filters and owning-tenant columns to the Skill list.
- [x] 3.2 Add tenant self-service entry, Markdown customize/reset actions and team-switch/conflict handling.

## 4. Verification and delivery

- [x] 4.1 Run backend authorization/runtime tests and frontend tests, type, Biome, antd checks and production builds.
- [x] 4.2 Prepare and verify deployment with database/Skill backups and document the release result.

## 5. Coexisting priorities and tenant-only visibility

- [x] 5.1 Restrict tenant reads to their own saved records; allow ordinary members to read while retaining owner/admin-only mutations and platform system CRUD.
- [x] 5.2 Preserve system and tenant layers in execution and snapshots with explicit tenant precedence; show priorities and add blank tenant supplement creation.
- [ ] 5.3 Verify API isolation, member permissions, coexistence and UI behavior, then deploy and record results.
