create table script_asset_normalization_snapshot (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  script_version_id bigint not null,
  execution_id bigint not null,
  input_fingerprint varchar(64) not null,
  input_json longtext not null,
  decision_json longtext null,
  result_json longtext null,
  status varchar(32) not null,
  agent_run_id bigint null,
  error_message text null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_asset_normalization_execution (execution_id),
  index idx_asset_normalization_script (tenant_id, project_id, script_id, id)
);
