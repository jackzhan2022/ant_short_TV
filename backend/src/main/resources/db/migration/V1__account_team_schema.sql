create table app_user (
  id bigint primary key auto_increment,
  mobile varchar(32) not null,
  email varchar(128) null,
  password_hash varchar(255) not null,
  nickname varchar(64) not null,
  avatar varchar(512) null,
  status varchar(32) not null,
  last_login_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  unique key uk_app_user_mobile (mobile),
  unique key uk_app_user_email (email),
  index idx_app_user_status (status)
);

create table tenant (
  id bigint primary key auto_increment,
  code varchar(32) not null,
  name varchar(64) not null,
  type varchar(32) not null,
  logo varchar(512) null,
  description text null,
  status varchar(32) not null,
  owner_member_id bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  unique key uk_tenant_code (code),
  index idx_tenant_status (status)
);

create table tenant_member (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  user_id bigint not null,
  member_type varchar(32) not null,
  status varchar(32) not null,
  joined_at datetime not null,
  invited_by bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_tenant_member_tenant_user (tenant_id, user_id),
  index idx_tenant_member_user_id (user_id),
  index idx_tenant_member_tenant_id (tenant_id),
  index idx_tenant_member_status (status)
);

create table tenant_invitation (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  invite_mobile varchar(32) not null,
  invite_user_id bigint null,
  invited_by bigint not null,
  token varchar(128) not null,
  status varchar(32) not null,
  expired_at datetime not null,
  accepted_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_tenant_invitation_token (token),
  index idx_tenant_invitation_tenant_id (tenant_id),
  index idx_tenant_invitation_invite_user_id (invite_user_id),
  index idx_tenant_invitation_invite_mobile (invite_mobile),
  index idx_tenant_invitation_status (status)
);

create table operation_log (
  id bigint primary key auto_increment,
  user_id bigint null,
  tenant_id bigint null,
  operation varchar(64) not null,
  target_id bigint null,
  result varchar(32) not null,
  ip varchar(64) null,
  user_agent varchar(512) null,
  created_at datetime not null,
  index idx_operation_log_user_id (user_id),
  index idx_operation_log_tenant_id (tenant_id),
  index idx_operation_log_operation (operation),
  index idx_operation_log_created_at (created_at)
);
