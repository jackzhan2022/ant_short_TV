create table scoped_asset_reextraction_snapshot (
  id bigint primary key auto_increment,
  operation_id bigint not null,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  asset_scope varchar(16) not null,
  prompt_policy varchar(24) not null,
  status varchar(32) not null,
  total_units int not null,
  completed_units int not null default 0,
  failed_units int not null default 0,
  created_at datetime not null,
  updated_at datetime not null,
  finished_at datetime null,
  unique key uk_scoped_asset_reextraction_operation (operation_id),
  index idx_scoped_asset_reextraction_project (tenant_id, project_id, script_id),
  constraint fk_scoped_asset_reextraction_operation foreign key (operation_id)
    references script_ai_operation(id) on delete cascade,
  constraint fk_scoped_asset_reextraction_project foreign key (project_id) references project(id),
  constraint fk_scoped_asset_reextraction_script foreign key (script_id) references script(id)
);

create table scoped_asset_reextraction_unit (
  id bigint primary key auto_increment,
  snapshot_id bigint not null,
  episode_id bigint not null,
  episode_key varchar(128) not null,
  content_fingerprint varchar(128) not null,
  status varchar(32) not null,
  child_run_id bigint null,
  error_message varchar(1000) null,
  created_at datetime not null,
  updated_at datetime not null,
  started_at datetime null,
  finished_at datetime null,
  unique key uk_scoped_asset_reextraction_unit (snapshot_id, episode_id),
  index idx_scoped_asset_reextraction_unit_status (snapshot_id, status),
  constraint fk_scoped_asset_reextraction_unit_snapshot foreign key (snapshot_id)
    references scoped_asset_reextraction_snapshot(id) on delete cascade,
  constraint fk_scoped_asset_reextraction_unit_episode foreign key (episode_id) references script_episode(id),
  constraint fk_scoped_asset_reextraction_unit_run foreign key (child_run_id)
    references ai_workflow_agent_run(id) on delete set null
);
