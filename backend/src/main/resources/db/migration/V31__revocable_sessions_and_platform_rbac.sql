alter table app_user
  add column token_version bigint not null default 0 after status;

create table auth_session (
  id bigint primary key auto_increment,
  session_id varchar(36) not null,
  user_id bigint not null,
  token_hash varchar(64) not null,
  token_version bigint not null,
  status varchar(32) not null,
  expires_at datetime not null,
  last_seen_at datetime not null,
  revoked_at datetime null,
  revoked_reason varchar(128) null,
  created_ip varchar(64) null,
  user_agent varchar(512) null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_auth_session_session_id (session_id),
  unique key uk_auth_session_token_hash (token_hash),
  index idx_auth_session_user_status (user_id, status),
  index idx_auth_session_expires_at (expires_at)
);

create table platform_permission (
  id bigint primary key auto_increment,
  code varchar(128) not null,
  name varchar(128) not null,
  resource varchar(64) not null,
  action varchar(64) not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_platform_permission_code (code),
  index idx_platform_permission_resource (resource)
);

create table platform_role (
  id bigint primary key auto_increment,
  code varchar(64) not null,
  name varchar(64) not null,
  description varchar(255) null,
  status varchar(32) not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_platform_role_code (code),
  index idx_platform_role_status (status)
);

create table platform_role_permission (
  id bigint primary key auto_increment,
  role_id bigint not null,
  permission_id bigint not null,
  created_at datetime not null,
  unique key uk_platform_role_permission_role_permission (role_id, permission_id),
  index idx_platform_role_permission_role_id (role_id),
  index idx_platform_role_permission_permission_id (permission_id)
);

create table platform_user_role (
  id bigint primary key auto_increment,
  user_id bigint not null,
  role_id bigint not null,
  created_at datetime not null,
  unique key uk_platform_user_role_user_role (user_id, role_id),
  index idx_platform_user_role_user_id (user_id),
  index idx_platform_user_role_role_id (role_id)
);

insert into platform_permission (code, name, resource, action, created_at, updated_at) values
  ('PLATFORM_AI_PROVIDER_VIEW', 'View platform AI providers', 'PLATFORM_AI_PROVIDER', 'VIEW', current_timestamp, current_timestamp),
  ('PLATFORM_AI_PROVIDER_CREATE', 'Create platform AI providers', 'PLATFORM_AI_PROVIDER', 'CREATE', current_timestamp, current_timestamp),
  ('PLATFORM_AI_PROVIDER_EDIT', 'Edit platform AI providers', 'PLATFORM_AI_PROVIDER', 'EDIT', current_timestamp, current_timestamp),
  ('PLATFORM_AI_PROVIDER_ENABLE', 'Enable platform AI providers', 'PLATFORM_AI_PROVIDER', 'ENABLE', current_timestamp, current_timestamp),
  ('PLATFORM_AI_PROVIDER_TEST', 'Test platform AI providers', 'PLATFORM_AI_PROVIDER', 'TEST', current_timestamp, current_timestamp),
  ('PLATFORM_AI_MODEL_VIEW', 'View platform AI models', 'PLATFORM_AI_MODEL', 'VIEW', current_timestamp, current_timestamp),
  ('PLATFORM_AI_MODEL_CREATE', 'Create platform AI models', 'PLATFORM_AI_MODEL', 'CREATE', current_timestamp, current_timestamp),
  ('PLATFORM_AI_MODEL_EDIT', 'Edit platform AI models', 'PLATFORM_AI_MODEL', 'EDIT', current_timestamp, current_timestamp),
  ('PLATFORM_AI_MODEL_ENABLE', 'Enable platform AI models', 'PLATFORM_AI_MODEL', 'ENABLE', current_timestamp, current_timestamp),
  ('PLATFORM_AI_AGENT_VIEW', 'View platform AI agents', 'PLATFORM_AI_AGENT', 'VIEW', current_timestamp, current_timestamp);

insert into platform_role (code, name, description, status, created_at, updated_at)
values ('PLATFORM_ADMIN', 'Platform Administrator', 'Full platform administration role', 'ACTIVE', current_timestamp, current_timestamp);

insert into platform_role_permission (role_id, permission_id, created_at)
select role.id, permission.id, current_timestamp
from platform_role role
cross join platform_permission permission
where role.code = 'PLATFORM_ADMIN';
