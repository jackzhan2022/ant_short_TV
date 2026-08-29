create table script_episode (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  script_version_id bigint not null,
  stable_key varchar(160) not null,
  episode_no int not null,
  title varchar(200) null,
  summary text null,
  content longtext not null,
  content_fingerprint varchar(64) not null,
  heading_key varchar(200) null,
  reconciliation_status varchar(32) not null,
  status varchar(32) not null,
  retired_at datetime null,
  active_stable_marker varchar(240) generated always as (
    case when retired_at is null
      then concat(tenant_id, ':', project_id, ':', script_id, ':', stable_key)
      else null
    end
  ),
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_script_episode_stable_key (active_stable_marker),
  index idx_script_episode_version_order (tenant_id, project_id, script_version_id, episode_no),
  index idx_script_episode_script_status (tenant_id, project_id, script_id, status),
  index idx_script_episode_fingerprint (tenant_id, project_id, script_id, content_fingerprint)
);

create table script_asset_normalization_run (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  script_version_id bigint not null,
  analysis_task_id bigint null,
  analysis_stage_id bigint null,
  analysis_result_id bigint null,
  execution_id bigint null,
  attempt_id bigint null,
  ai_call_log_id bigint null,
  idempotency_key varchar(200) not null,
  schema_version varchar(32) not null,
  status varchar(32) not null,
  raw_response longtext null,
  normalized_json longtext null,
  error_code varchar(64) null,
  error_message text null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_script_asset_normalization_run_idempotency (tenant_id, idempotency_key),
  index idx_script_asset_normalization_run_script (tenant_id, project_id, script_version_id, created_at),
  index idx_script_asset_normalization_run_execution (execution_id, attempt_id),
  index idx_script_asset_normalization_run_analysis (analysis_task_id, analysis_stage_id)
);

create table script_asset_candidate (
  id bigint primary key auto_increment,
  run_id bigint not null,
  tenant_id bigint not null,
  project_id bigint not null,
  asset_type varchar(32) not null,
  source_index int not null,
  source_key varchar(64) null,
  name varchar(100) null,
  normalized_name varchar(100) null,
  candidate_json longtext not null,
  validation_status varchar(32) not null,
  validation_errors_json longtext null,
  duplicate_group_key varchar(160) null,
  proposed_target_id bigint null,
  match_type varchar(32) null,
  match_confidence decimal(5,4) null,
  match_evidence_json longtext null,
  review_status varchar(32) not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_script_asset_candidate_source (run_id, asset_type, source_index),
  index idx_script_asset_candidate_review (tenant_id, project_id, review_status, asset_type),
  index idx_script_asset_candidate_group (tenant_id, project_id, asset_type, duplicate_group_key),
  index idx_script_asset_candidate_target (tenant_id, project_id, asset_type, proposed_target_id)
);

create table script_asset_candidate_alias (
  id bigint primary key auto_increment,
  candidate_id bigint not null,
  alias_name varchar(100) not null,
  normalized_alias varchar(100) not null,
  source varchar(32) not null,
  evidence_json longtext null,
  created_at datetime not null,
  unique key uk_script_asset_candidate_alias (candidate_id, normalized_alias),
  index idx_script_asset_candidate_alias_normalized (normalized_alias)
);

create table script_asset_promotion_decision (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  candidate_id bigint not null,
  decision_type varchar(32) not null,
  requested_target_id bigint null,
  result_asset_id bigint null,
  idempotency_key varchar(200) not null,
  status varchar(32) not null,
  error_code varchar(64) null,
  error_message text null,
  decided_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_script_asset_promotion_decision_idempotency (tenant_id, idempotency_key),
  index idx_script_asset_promotion_candidate (tenant_id, project_id, candidate_id, created_at)
);

create table asset_visual_variant (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  asset_type varchar(32) not null,
  asset_id bigint not null,
  name varchar(100) not null,
  appearance text null,
  prompt text null,
  source_type varchar(32) not null,
  generation_status varchar(32) not null,
  generation_task_id bigint null,
  current_image_result_id bigint null,
  current_image_url varchar(1000) null,
  generation_error_code varchar(64) null,
  generation_error_message text null,
  is_primary boolean not null,
  primary_marker varchar(180) generated always as (
    case when is_primary = true and deleted_at is null
      then concat(tenant_id, ':', project_id, ':', asset_type, ':', asset_id)
      else null
    end
  ),
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  unique key uk_asset_visual_variant_primary (primary_marker),
  index idx_asset_visual_variant_owner (tenant_id, project_id, asset_type, asset_id, deleted_at),
  index idx_asset_visual_variant_generation (tenant_id, project_id, generation_status),
  index idx_asset_visual_variant_image_result (current_image_result_id)
);

create table asset_visual_variant_episode (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  episode_id bigint not null,
  asset_type varchar(32) not null,
  asset_id bigint not null,
  variant_id bigint not null,
  is_preferred boolean not null,
  binding_status varchar(32) not null,
  active_binding_marker varchar(160) generated always as (
    case when retired_at is null
      then concat(variant_id, ':', episode_id)
      else null
    end
  ),
  preferred_marker varchar(220) generated always as (
    case when is_preferred = true and retired_at is null and binding_status = 'ACTIVE'
      then concat(tenant_id, ':', project_id, ':', episode_id, ':', asset_type, ':', asset_id)
      else null
    end
  ),
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  retired_at datetime null,
  unique key uk_asset_visual_variant_episode_preferred (preferred_marker),
  unique key uk_asset_visual_variant_episode_binding (active_binding_marker),
  index idx_asset_visual_variant_episode_episode (tenant_id, project_id, episode_id, asset_type),
  index idx_asset_visual_variant_episode_owner (tenant_id, project_id, asset_type, asset_id),
  index idx_asset_visual_variant_episode_variant (variant_id)
);

alter table prop_asset
  add column main_image_url varchar(1000) null;

alter table prop_asset
  add column main_image_result_id bigint null;
