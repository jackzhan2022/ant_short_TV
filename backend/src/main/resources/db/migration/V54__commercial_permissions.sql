insert into platform_permission (code, name, resource, action, created_at, updated_at)
values
  ('PLATFORM_COMMERCIAL_PACKAGE_EDIT', 'Edit commercial packages', 'PLATFORM_COMMERCIAL_PACKAGE', 'EDIT', now(), now()),
  ('PLATFORM_COMMERCIAL_ORDER_VIEW', 'View commercial orders', 'PLATFORM_COMMERCIAL_ORDER', 'VIEW', now(), now()),
  ('PLATFORM_COMMERCIAL_SUBSCRIPTION_ADJUST', 'Adjust commercial subscriptions', 'PLATFORM_COMMERCIAL_SUBSCRIPTION', 'ADJUST', now(), now());

insert into platform_role_permission (role_id, permission_id, created_at)
select role.id, permission.id, now()
  from platform_role role
  join platform_permission permission
    on permission.code in (
      'PLATFORM_COMMERCIAL_PACKAGE_EDIT',
      'PLATFORM_COMMERCIAL_ORDER_VIEW',
      'PLATFORM_COMMERCIAL_SUBSCRIPTION_ADJUST'
    )
 where role.code = 'PLATFORM_ADMIN';

insert into permission (code, name, type, resource, action, created_at, updated_at)
values ('BILLING:MANAGE', '管理团队套餐与订单', 'PAGE', 'BILLING', 'MANAGE', now(), now());

insert into role_permission (role_id, permission_id, created_at)
select role.id, permission.id, now()
  from `role` role
  join permission on permission.code = 'BILLING:MANAGE'
 where role.code in ('OWNER', 'ADMIN')
   and role.deleted_at is null;
