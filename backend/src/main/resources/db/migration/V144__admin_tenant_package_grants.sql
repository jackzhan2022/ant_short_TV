create table admin_authenticator (
  user_id bigint primary key,
  secret_cipher text null,
  pending_secret_cipher text null,
  pending_expires_at datetime null,
  last_used_step bigint not null default -1,
  failed_attempts int not null default 0,
  locked_until datetime null,
  updated_at datetime not null
);

create table admin_tenant_package_grant (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  operator_id bigint not null,
  package_version_id bigint not null,
  package_snapshot_json text not null,
  subscription_id bigint null,
  reason varchar(500) not null,
  request_id varchar(64) not null,
  status varchar(32) not null,
  created_at datetime not null,
  unique key uk_admin_package_grant_request (operator_id, request_id),
  index idx_admin_package_grant_tenant (tenant_id, created_at),
  index idx_admin_package_grant_subscription (subscription_id)
);

alter table team_subscription modify column source_order_id bigint null;
alter table team_subscription add column source_admin_grant_id bigint null;
alter table commercial_entitlement_grant add column admin_grant_id bigint null;
