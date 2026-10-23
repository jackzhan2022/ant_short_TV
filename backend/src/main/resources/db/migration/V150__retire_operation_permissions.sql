-- Preserve the existing project visibility boundary independently of menu grants.
alter table `role` add column project_access_all boolean not null default false;
update `role` set project_access_all = true where id in (
  select rp.role_id from role_permission rp join permission p on p.id = rp.permission_id
  where p.code = 'PROJECT:VIEW_ALL'
);

-- Project roles retain the project-access marker only; team menus authorize actions.
insert into project_role_permission (tenant_id, project_id, role_id, permission_id, created_at)
select rp.tenant_id, rp.project_id, rp.role_id, menu.id, current_timestamp
from project_role_permission rp
join permission old_permission on old_permission.id = rp.permission_id and old_permission.code = 'PROJECT:VIEW'
cross join permission menu
where menu.code = 'MENU:PROJECTS' and not exists (
  select 1 from project_role_permission existing
  where existing.tenant_id = rp.tenant_id and existing.project_id = rp.project_id
    and existing.role_id = rp.role_id and existing.permission_id = menu.id
);

-- V149 made navigation visible to legacy members. Do not turn that into new administrative authority.
-- Explicit menu assignments on newly created/updated custom roles remain intact.
delete from role_permission where permission_id in (select id from permission where code = 'MENU:TEAM_MANAGEMENT')
and role_id in (
  select r.id from `role` r where r.code not in ('OWNER', 'ADMIN')
    and ((r.role_type = 'SYSTEM' and r.code = 'MEMBER') or r.updated_at <= (
      select installed_on from flyway_schema_history where version = '149' and success = true))
    and r.id not in (
      select eligible.role_id from (
        select distinct rp.role_id from role_permission rp join permission p on p.id = rp.permission_id
        where p.code in ('ROLE:CREATE', 'ROLE:EDIT', 'ROLE:DELETE', 'MEMBER:INVITE', 'MEMBER:REMOVE', 'TENANT:EDIT', 'BILLING:MANAGE')
      ) eligible
    )
);
delete from role_permission where permission_id in (select id from permission where code = 'MENU:AI_MANAGEMENT')
and role_id in (
  select r.id from `role` r where r.code not in ('OWNER', 'ADMIN')
    and ((r.role_type = 'SYSTEM' and r.code = 'MEMBER') or r.updated_at <= (
      select installed_on from flyway_schema_history where version = '149' and success = true))
    and r.id not in (
      select eligible.role_id from (
        select distinct rp.role_id from role_permission rp join permission p on p.id = rp.permission_id
        where p.code = 'AI_CALL_LOG:VIEW'
      ) eligible
    )
);

delete from project_role_permission where permission_id in (select id from permission where type <> 'MENU');
delete from role_permission where permission_id in (select id from permission where type <> 'MENU');
delete from permission where type <> 'MENU';
