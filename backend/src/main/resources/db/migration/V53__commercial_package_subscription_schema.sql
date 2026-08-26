create table commercial_package (
  id bigint primary key auto_increment,
  code varchar(64) not null,
  package_type varchar(32) not null,
  status varchar(32) not null,
  created_by bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_commercial_package_code (code),
  index idx_commercial_package_status (status, package_type)
);

create table commercial_package_version (
  id bigint primary key auto_increment,
  package_id bigint not null,
  version_no int not null,
  name varchar(128) not null,
  description varchar(1000) null,
  billing_period varchar(32) null,
  period_months int null,
  price decimal(18,2) not null,
  list_price decimal(18,2) null,
  currency varchar(8) not null default 'CNY',
  effective_from datetime not null,
  effective_to datetime null,
  status varchar(32) not null,
  published_at datetime null,
  created_by bigint null,
  created_at datetime not null,
  unique key uk_commercial_package_version (package_id, version_no),
  index idx_commercial_package_version_sale (status, effective_from, effective_to)
);

create table commercial_entitlement (
  id bigint primary key auto_increment,
  package_version_id bigint not null,
  entitlement_type varchar(48) not null,
  numeric_value decimal(24,8) null,
  text_value varchar(255) null,
  config_json text null,
  created_at datetime not null,
  unique key uk_commercial_entitlement_type (package_version_id, entitlement_type),
  index idx_commercial_entitlement_package (package_version_id)
);

create table commercial_order (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  user_id bigint not null,
  package_version_id bigint not null,
  package_snapshot_json text not null,
  merchant_order_no varchar(64) not null,
  amount decimal(18,2) not null,
  currency varchar(8) not null default 'CNY',
  status varchar(32) not null,
  expires_at datetime not null,
  paid_at datetime null,
  completed_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_commercial_order_merchant_no (merchant_order_no),
  index idx_commercial_order_tenant_status (tenant_id, status, created_at),
  index idx_commercial_order_expiry (status, expires_at)
);

create table commercial_payment (
  id bigint primary key auto_increment,
  order_id bigint not null,
  provider varchar(32) not null,
  provider_trade_no varchar(128) null,
  prepay_id varchar(128) null,
  code_url varchar(1024) null,
  amount decimal(18,2) not null,
  status varchar(32) not null,
  paid_at datetime null,
  raw_response_json text null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_commercial_payment_provider_trade_no (provider, provider_trade_no),
  unique key uk_commercial_payment_order_provider (order_id, provider),
  index idx_commercial_payment_status (status, updated_at)
);

create table team_subscription (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  package_version_id bigint not null,
  source_order_id bigint not null,
  status varchar(32) not null,
  starts_at datetime not null,
  ends_at datetime not null,
  next_grant_at datetime null,
  snapshot_json text not null,
  created_at datetime not null,
  updated_at datetime not null,
  index idx_team_subscription_active (tenant_id, status, starts_at, ends_at),
  index idx_team_subscription_grant (status, next_grant_at)
);

create table commercial_entitlement_grant (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  order_id bigint null,
  subscription_id bigint null,
  period_no int null,
  entitlement_type varchar(48) not null,
  amount decimal(24,8) null,
  status varchar(32) not null,
  idempotency_key varchar(200) not null,
  granted_at datetime null,
  error_message varchar(1000) null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_commercial_grant_idempotency (tenant_id, idempotency_key),
  unique key uk_commercial_grant_subscription_period (subscription_id, period_no, entitlement_type),
  index idx_commercial_grant_tenant_created (tenant_id, created_at)
);

create table commercial_payment_event (
  id bigint primary key auto_increment,
  order_id bigint null,
  provider varchar(32) not null,
  event_type varchar(48) not null,
  provider_event_id varchar(128) null,
  payload_json text not null,
  processed boolean not null default false,
  created_at datetime not null,
  unique key uk_commercial_payment_event (provider, provider_event_id),
  index idx_commercial_payment_event_order (order_id, created_at)
);

create table commercial_audit (
  id bigint primary key auto_increment,
  tenant_id bigint null,
  user_id bigint null,
  operator_type varchar(32) not null,
  operation varchar(64) not null,
  target_type varchar(64) not null,
  target_id bigint null,
  detail_json text null,
  created_at datetime not null,
  index idx_commercial_audit_tenant_created (tenant_id, created_at),
  index idx_commercial_audit_target (target_type, target_id)
);
