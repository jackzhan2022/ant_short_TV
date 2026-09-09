create table episode_auto_storyboard_event (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  episode_id bigint not null,
  episode_key varchar(128) not null,
  source_fingerprint varchar(128) not null,
  asset_analysis_id bigint not null,
  context_snapshot_id bigint null,
  policy_version varchar(32) not null,
  status varchar(32) not null,
  attempt_no int not null default 0,
  execution_id bigint null,
  error_code varchar(128) null,
  error_message varchar(1000) null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  next_attempt_at datetime null,
  finished_at datetime null,
  unique key uk_episode_auto_storyboard_source
    (tenant_id, script_id, episode_id, source_fingerprint, policy_version),
  key idx_episode_auto_storyboard_dispatch (status, next_attempt_at, id),
  constraint fk_episode_auto_storyboard_episode foreign key (episode_id) references script_episode(id),
  constraint fk_episode_auto_storyboard_analysis foreign key (asset_analysis_id)
    references script_episode_asset_analysis(id),
  constraint fk_episode_auto_storyboard_context foreign key (context_snapshot_id)
    references episode_prompt_context_snapshot(id),
  constraint fk_episode_auto_storyboard_execution foreign key (execution_id)
    references ai_execution_task(id)
);
