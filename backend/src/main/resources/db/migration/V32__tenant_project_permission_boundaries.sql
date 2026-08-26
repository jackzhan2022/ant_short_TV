delete from role_permission
where permission_id in (
  select id from permission where code like 'PLATFORM!_%' escape '!'
);

delete from permission where code like 'PLATFORM!_%' escape '!';

insert into permission (code, name, type, resource, action, created_at, updated_at) values
  ('PROJECT:VIEW_ALL', 'View all tenant projects', 'PAGE', 'PROJECT', 'VIEW_ALL', current_timestamp, current_timestamp),
  ('PROJECT:EDIT_ALL', 'Edit all tenant projects', 'BUTTON', 'PROJECT', 'EDIT_ALL', current_timestamp, current_timestamp),
  ('PROJECT:DELETE_ALL', 'Delete all tenant projects', 'BUTTON', 'PROJECT', 'DELETE_ALL', current_timestamp, current_timestamp);

insert into role_permission (role_id, permission_id, created_at)
select role.id, permission.id, current_timestamp
from `role` role
cross join permission permission
where role.code in ('OWNER', 'ADMIN')
  and role.deleted_at is null
  and permission.code in ('PROJECT:VIEW_ALL', 'PROJECT:EDIT_ALL', 'PROJECT:DELETE_ALL')
  and not exists (
    select 1
    from role_permission existing
    where existing.role_id = role.id and existing.permission_id = permission.id
  );
