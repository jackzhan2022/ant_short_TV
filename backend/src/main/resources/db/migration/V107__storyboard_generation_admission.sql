create table storyboard_generation_admission (
  tenant_id bigint not null,
  project_id bigint not null,
  episode_id bigint not null,
  source_fingerprint varchar(128) not null,
  execution_id bigint null,
  origin varchar(32) not null,
  created_at datetime not null,
  updated_at datetime not null,
  primary key (tenant_id, project_id, episode_id, source_fingerprint),
  constraint fk_storyboard_admission_execution foreign key (execution_id)
    references ai_execution_task(id)
);
