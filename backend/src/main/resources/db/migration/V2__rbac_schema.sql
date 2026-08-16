create table permission (
  id bigint primary key auto_increment,
  code varchar(128) not null,
  name varchar(128) not null,
  type varchar(32) not null,
  resource varchar(64) not null,
  action varchar(64) not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_permission_code (code),
  index idx_permission_resource (resource)
);

create table `role` (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  code varchar(64) not null,
  name varchar(64) not null,
  description varchar(255) null,
  role_type varchar(32) not null,
  status varchar(32) not null,
  is_default boolean not null,
  created_by bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  unique key uk_role_tenant_code (tenant_id, code),
  index idx_role_tenant_id (tenant_id),
  index idx_role_status (status)
);

create table role_permission (
  id bigint primary key auto_increment,
  role_id bigint not null,
  permission_id bigint not null,
  created_at datetime not null,
  unique key uk_role_permission_role_permission (role_id, permission_id),
  index idx_role_permission_role_id (role_id),
  index idx_role_permission_permission_id (permission_id)
);

create table member_role (
  id bigint primary key auto_increment,
  member_id bigint not null,
  role_id bigint not null,
  created_by bigint null,
  created_at datetime not null,
  unique key uk_member_role_member_role (member_id, role_id),
  index idx_member_role_member_id (member_id),
  index idx_member_role_role_id (role_id)
);
