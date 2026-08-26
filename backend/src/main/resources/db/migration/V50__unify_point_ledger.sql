create table point_ledger (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  user_id bigint null,
  execution_id bigint null,
  execution_version int null,
  business_type varchar(64) null,
  business_id bigint null,
  reservation_id bigint null,
  attempt_id bigint null,
  ai_call_log_id bigint null,
  policy_version_id bigint null,
  entry_type varchar(32) not null,
  amount decimal(24,8) not null,
  available_balance_after decimal(24,8) not null,
  reserved_balance_after decimal(24,8) not null,
  idempotency_key varchar(200) not null,
  description varchar(500) null,
  created_at datetime not null,
  constraint uk_point_ledger_idempotency unique (tenant_id, idempotency_key),
  index idx_point_ledger_tenant_created (tenant_id, created_at, id),
  index idx_point_ledger_execution (execution_id, execution_version, id),
  index idx_point_ledger_business (tenant_id, business_type, business_id, id),
  index idx_point_ledger_reservation (reservation_id, id)
);

alter table ai_point_reservation
  add constraint uk_ai_point_reservation_execution_version unique (execution_id, execution_version);

drop table ai_point_ledger;
drop table team_point_transaction;
