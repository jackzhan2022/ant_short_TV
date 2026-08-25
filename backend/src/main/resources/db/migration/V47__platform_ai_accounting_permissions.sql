insert into platform_permission
  (code, name, resource, action, created_at, updated_at)
values
  ('PLATFORM_AI_PRICE_PUBLISH', 'Publish AI model price versions', 'PLATFORM_AI_PRICE', 'PUBLISH', now(), now()),
  ('PLATFORM_AI_POINT_POLICY_PUBLISH', 'Publish AI point policy versions', 'PLATFORM_AI_POINT_POLICY', 'PUBLISH', now(), now()),
  ('PLATFORM_AI_ACCOUNTING_VIEW', 'View AI accounting details', 'PLATFORM_AI_ACCOUNTING', 'VIEW', now(), now());

insert into platform_role_permission (role_id, permission_id, created_at)
select role.id, permission.id, now()
  from platform_role role
  join platform_permission permission on permission.code in (
    'PLATFORM_AI_PRICE_PUBLISH',
    'PLATFORM_AI_POINT_POLICY_PUBLISH',
    'PLATFORM_AI_ACCOUNTING_VIEW'
  )
 where role.code = 'PLATFORM_ADMIN';
