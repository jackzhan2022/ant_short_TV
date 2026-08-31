create table script_episode_asset_analysis (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  episode_id bigint not null,
  schema_version int not null,
  content_fingerprint varchar(128) not null,
  content_json longtext not null,
  generated_by_run_id bigint null,
  created_by bigint not null,
  updated_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_episode_asset_analysis_episode (tenant_id, episode_id),
  index idx_episode_asset_analysis_script (tenant_id, project_id, script_id),
  index idx_episode_asset_analysis_run (generated_by_run_id),
  constraint fk_episode_asset_analysis_project foreign key (project_id) references project(id),
  constraint fk_episode_asset_analysis_script foreign key (script_id) references script(id),
  constraint fk_episode_asset_analysis_episode foreign key (episode_id) references script_episode(id),
  constraint fk_episode_asset_analysis_run foreign key (generated_by_run_id)
    references ai_workflow_agent_run(id) on delete set null
);

create table script_asset_identity_lock (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  asset_type varchar(32) not null,
  normalized_name varchar(100) not null,
  created_at datetime not null,
  unique key uk_script_asset_identity_lock
    (tenant_id, project_id, script_id, asset_type, normalized_name),
  constraint fk_script_asset_identity_lock_project foreign key (project_id) references project(id),
  constraint fk_script_asset_identity_lock_script foreign key (script_id) references script(id)
);

alter table asset_visual_variant_episode
  add column content_json longtext null;
