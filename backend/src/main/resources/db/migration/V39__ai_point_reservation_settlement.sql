alter table team_point_account modify column balance decimal(24,8) not null;
alter table team_point_account modify column total_granted decimal(24,8) not null;
alter table team_point_account modify column total_consumed decimal(24,8) not null;
alter table team_point_account add column reserved_balance decimal(24,8) not null default 0;
alter table team_point_account add column total_reserved decimal(24,8) not null default 0;
alter table team_point_account add column total_released decimal(24,8) not null default 0;
alter table team_point_account add column total_refunded decimal(24,8) not null default 0;
alter table team_point_account add column version int not null default 0;

create table ai_point_policy_version (
  id bigint primary key auto_increment,
  scene varchar(100) not null,
  model_id bigint null,
  capability varchar(64) null,
  version_no int not null,
  status varchar(32) not null,
  effective_from datetime not null,
  effective_to datetime null,
  charge_provider_rejection boolean not null default false,
  charge_provider_billed_failure boolean not null default true,
  charge_timeout boolean not null default true,
  charge_business_failure boolean not null default true,
  created_at datetime not null,
  published_at datetime null,
  constraint uk_ai_point_policy_version unique (scene, version_no),
  index idx_ai_point_policy_effective (scene, status, effective_from, effective_to)
);

create table ai_point_policy_component (
  id bigint primary key auto_increment,
  policy_version_id bigint not null,
  metric varchar(32) not null,
  unit_size decimal(24,8) not null,
  point_rate decimal(24,8) not null,
  dimensions_json text null,
  dimensions_key varchar(500) not null default '',
  created_at datetime not null,
  constraint fk_ai_point_policy_component foreign key (policy_version_id) references ai_point_policy_version(id),
  constraint uk_ai_point_policy_component unique (policy_version_id, metric, dimensions_key)
);

create table ai_point_reservation (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  user_id bigint not null,
  execution_id bigint not null,
  execution_version int not null,
  business_type varchar(64) not null,
  business_id bigint null,
  scene varchar(100) not null,
  policy_version_id bigint not null,
  status varchar(32) not null,
  authorized_usage_json text null,
  dimensions_json text null,
  reserved_points decimal(24,8) not null,
  settled_points decimal(24,8) not null default 0,
  released_points decimal(24,8) not null default 0,
  refunded_points decimal(24,8) not null default 0,
  idempotency_key varchar(200) not null,
  created_at datetime not null,
  settled_at datetime null,
  released_at datetime null,
  refunded_at datetime null,
  updated_at datetime not null,
  constraint fk_ai_point_reservation_policy foreign key (policy_version_id) references ai_point_policy_version(id),
  constraint uk_ai_point_reservation_execution unique (execution_id, execution_version),
  constraint uk_ai_point_reservation_idempotency unique (tenant_id, idempotency_key),
  index idx_ai_point_reservation_tenant_status (tenant_id, status, id)
);

create table ai_point_ledger (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  user_id bigint not null,
  execution_id bigint not null,
  execution_version int not null,
  business_type varchar(64) not null,
  business_id bigint null,
  reservation_id bigint not null,
  attempt_id bigint null,
  ai_call_log_id bigint null,
  policy_version_id bigint not null,
  entry_type varchar(32) not null,
  amount decimal(24,8) not null,
  available_balance_after decimal(24,8) not null,
  reserved_balance_after decimal(24,8) not null,
  idempotency_key varchar(200) not null,
  created_at datetime not null,
  constraint fk_ai_point_ledger_reservation foreign key (reservation_id) references ai_point_reservation(id),
  constraint uk_ai_point_ledger_idempotency unique (tenant_id, idempotency_key),
  index idx_ai_point_ledger_execution (execution_id, execution_version, id),
  index idx_ai_point_ledger_business (tenant_id, business_type, business_id, id),
  index idx_ai_point_ledger_attempt_call (attempt_id, ai_call_log_id)
);
