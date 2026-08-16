create table organization (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  parent_id bigint null,
  name varchar(100) not null,
  code varchar(50) not null,
  level int not null,
  leader_id bigint null,
  sort int not null,
  status varchar(32) not null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  unique key uk_organization_tenant_code (tenant_id, code),
  index idx_organization_tenant_id (tenant_id),
  index idx_organization_tenant_parent (tenant_id, parent_id),
  index idx_organization_status (status)
);

create table organization_member (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  organization_id bigint not null,
  user_id bigint not null,
  is_primary boolean not null,
  joined_at datetime not null,
  created_at datetime not null,
  unique key uk_organization_member_tenant_org_user (tenant_id, organization_id, user_id),
  index idx_organization_member_tenant_org (tenant_id, organization_id),
  index idx_organization_member_tenant_user (tenant_id, user_id)
);

create table project (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  organization_id bigint null,
  name varchar(200) not null,
  code varchar(50) not null,
  description text null,
  cover_url varchar(500) null,
  owner_id bigint not null,
  status varchar(30) not null,
  start_date date null,
  end_date date null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  unique key uk_project_tenant_code (tenant_id, code),
  index idx_project_tenant_status (tenant_id, status),
  index idx_project_tenant_organization (tenant_id, organization_id),
  index idx_project_tenant_owner (tenant_id, owner_id)
);

create table project_role (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  name varchar(100) not null,
  code varchar(50) not null,
  description varchar(500) null,
  is_system boolean not null,
  status varchar(32) not null,
  data_scope varchar(32) not null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_project_role_project_code (project_id, code),
  index idx_project_role_tenant_project (tenant_id, project_id)
);

create table project_role_permission (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  role_id bigint not null,
  permission_id bigint not null,
  created_at datetime not null,
  unique key uk_project_role_permission_project_role_permission (project_id, role_id, permission_id),
  index idx_project_role_permission_tenant_project (tenant_id, project_id),
  index idx_project_role_permission_role (role_id)
);

create table project_member (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  user_id bigint not null,
  organization_id bigint null,
  role_id bigint not null,
  joined_at datetime not null,
  status varchar(32) not null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_project_member_tenant_project_user (tenant_id, project_id, user_id),
  index idx_project_member_tenant_project (tenant_id, project_id),
  index idx_project_member_tenant_user (tenant_id, user_id),
  index idx_project_member_role (role_id)
);

create table project_operation_log (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint null,
  user_id bigint null,
  operation_type varchar(64) not null,
  resource_type varchar(64) not null,
  resource_id bigint null,
  before_data text null,
  after_data text null,
  ip varchar(64) null,
  user_agent varchar(512) null,
  created_at datetime not null,
  index idx_project_operation_log_tenant_project (tenant_id, project_id),
  index idx_project_operation_log_created_at (created_at)
);
