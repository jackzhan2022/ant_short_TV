create table episode_prompt_context_snapshot (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  episode_id bigint not null,
  model_id bigint not null,
  source_fingerprint varchar(64) not null,
  global_understanding_hash varchar(64) not null,
  context_hash varchar(64) not null,
  rules_revision varchar(64) not null,
  tool_protocol_revision varchar(64) not null,
  common_prefix longtext not null,
  created_at datetime not null default current_timestamp,
  unique key uk_episode_prompt_context_identity (tenant_id, model_id, context_hash),
  key idx_episode_prompt_context_episode (tenant_id, project_id, script_id, episode_id)
);
