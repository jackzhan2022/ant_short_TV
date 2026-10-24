create table prop_simplification_plan (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  script_version_id bigint not null,
  execution_id bigint not null,
  input_fingerprint varchar(64) not null,
  input_json longtext not null,
  decision_json longtext null,
  previous_policies_json longtext null,
  applied_variant_ids_json longtext null,
  status varchar(32) not null,
  error_message text null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_prop_simplification_execution (execution_id),
  index idx_prop_simplification_project (tenant_id,project_id,id)
);

create table prop_production_policy (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  script_version_id bigint not null,
  source_asset_id bigint not null,
  plan_id bigint not null,
  policy_json longtext not null,
  updated_at datetime not null,
  unique key uk_prop_production_source (tenant_id,project_id,source_asset_id),
  index idx_prop_production_plan (tenant_id,project_id,plan_id)
);
