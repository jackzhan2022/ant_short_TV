create table customer_service_config (
  id int primary key,
  qr_code_path varchar(512) null,
  updated_at datetime not null
);

insert into customer_service_config(id, updated_at) values (1, current_timestamp);

insert into platform_permission(code, name, resource, action, created_at, updated_at) values
  ('PLATFORM_CUSTOMER_SERVICE_VIEW', 'View customer service configuration', 'PLATFORM_CUSTOMER_SERVICE', 'VIEW', current_timestamp, current_timestamp),
  ('PLATFORM_CUSTOMER_SERVICE_EDIT', 'Edit customer service configuration', 'PLATFORM_CUSTOMER_SERVICE', 'EDIT', current_timestamp, current_timestamp);

insert into platform_role_permission(role_id, permission_id, created_at)
select role.id, permission.id, current_timestamp
from platform_role role
join platform_permission permission on permission.code in ('PLATFORM_CUSTOMER_SERVICE_VIEW', 'PLATFORM_CUSTOMER_SERVICE_EDIT')
where role.code = 'PLATFORM_ADMIN';
