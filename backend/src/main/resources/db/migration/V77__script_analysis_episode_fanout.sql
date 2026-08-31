create table script_analysis_fanout_snapshot (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  task_id bigint not null,
  stage_id bigint not null,
  stage_code varchar(64) not null,
  attempt_no int not null,
  agent_code varchar(128) not null,
  agent_revision bigint not null,
  model_id bigint not null,
  episode_set_hash varchar(64) not null,
  status varchar(32) not null,
  total_units int not null,
  completed_units int not null default 0,
  failed_units int not null default 0,
  created_at datetime not null,
  updated_at datetime not null,
  finished_at datetime null,
  cancelled_at datetime null,
  unique key uk_script_analysis_fanout_attempt (stage_id, attempt_no),
  index idx_script_analysis_fanout_task (tenant_id, task_id, stage_id),
  constraint fk_script_analysis_fanout_project foreign key (project_id) references project(id),
  constraint fk_script_analysis_fanout_script foreign key (script_id) references script(id),
  constraint fk_script_analysis_fanout_task foreign key (task_id) references script_analysis_task(id),
  constraint fk_script_analysis_fanout_stage foreign key (stage_id) references script_analysis_stage(id),
  constraint fk_script_analysis_fanout_model foreign key (model_id) references ai_model(id)
);

create table script_analysis_fanout_unit (
  id bigint primary key auto_increment,
  snapshot_id bigint not null,
  episode_id bigint not null,
  episode_key varchar(128) not null,
  content_fingerprint varchar(128) not null,
  status varchar(32) not null,
  child_run_id bigint null,
  attempt_no int not null default 0,
  error_code varchar(128) null,
  error_message varchar(1000) null,
  created_at datetime not null,
  updated_at datetime not null,
  started_at datetime null,
  finished_at datetime null,
  unique key uk_script_analysis_fanout_unit (snapshot_id, episode_id),
  index idx_script_analysis_fanout_unit_status (snapshot_id, status),
  constraint fk_script_analysis_fanout_unit_snapshot foreign key (snapshot_id)
    references script_analysis_fanout_snapshot(id) on delete cascade,
  constraint fk_script_analysis_fanout_unit_episode foreign key (episode_id)
    references script_episode(id),
  constraint fk_script_analysis_fanout_unit_run foreign key (child_run_id)
    references ai_workflow_agent_run(id) on delete set null
);
