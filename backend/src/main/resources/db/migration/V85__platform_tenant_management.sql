alter table operation_log
  add column detail_json text null;

create index idx_tenant_platform_list
  on tenant (status, deleted_at, created_at);

create index idx_tenant_member_tenant_status
  on tenant_member (tenant_id, status);

insert into platform_permission (code, name, resource, action, created_at, updated_at)
values
  ('PLATFORM_TENANT_VIEW', 'View platform tenants', 'PLATFORM_TENANT', 'VIEW', now(), now()),
  ('PLATFORM_TENANT_STATUS_EDIT', 'Edit platform tenant status', 'PLATFORM_TENANT', 'STATUS_EDIT', now(), now());

insert into platform_role_permission (role_id, permission_id, created_at)
select role.id, permission.id, now()
  from platform_role role
  join platform_permission permission
    on permission.code in ('PLATFORM_TENANT_VIEW', 'PLATFORM_TENANT_STATUS_EDIT')
 where role.code = 'PLATFORM_ADMIN';
