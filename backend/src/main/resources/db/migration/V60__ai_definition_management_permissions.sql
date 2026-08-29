insert into platform_permission (code, name, resource, action, created_at, updated_at)
select 'PLATFORM_AI_AGENT_EDIT', 'Edit platform AI agents', 'PLATFORM_AI_AGENT', 'EDIT', current_timestamp, current_timestamp
where not exists (select 1 from platform_permission where code = 'PLATFORM_AI_AGENT_EDIT');
insert into platform_permission (code, name, resource, action, created_at, updated_at)
select 'PLATFORM_AI_SKILL_EDIT', 'Edit platform AI skills', 'PLATFORM_AI_SKILL', 'EDIT', current_timestamp, current_timestamp
where not exists (select 1 from platform_permission where code = 'PLATFORM_AI_SKILL_EDIT');

insert into platform_role_permission (role_id, permission_id, created_at)
select role.id, permission.id, current_timestamp
from platform_role role
join platform_permission permission on permission.code in ('PLATFORM_AI_AGENT_EDIT', 'PLATFORM_AI_SKILL_EDIT')
where role.code = 'PLATFORM_ADMIN'
  and not exists (
    select 1 from platform_role_permission existing
    where existing.role_id = role.id and existing.permission_id = permission.id
  );
