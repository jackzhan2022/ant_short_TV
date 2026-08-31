create table script_global_understanding (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  schema_version int not null,
  content_json longtext not null,
  analyzed_content_hash varchar(64) not null,
  last_agent_run_id bigint null,
  created_by bigint not null,
  updated_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_script_global_understanding_script (tenant_id, script_id),
  index idx_script_global_understanding_project (tenant_id, project_id),
  index idx_script_global_understanding_run (last_agent_run_id),
  constraint fk_script_global_understanding_project foreign key (project_id) references project(id),
  constraint fk_script_global_understanding_script foreign key (script_id) references script(id),
  constraint fk_script_global_understanding_run foreign key (last_agent_run_id)
    references ai_workflow_agent_run(id) on delete set null
);

alter table ai_workflow_agent_run
  add column script_id bigint null;
alter table ai_workflow_agent_run
  add column analysis_stage_id bigint null;
create index idx_ai_workflow_agent_run_script
  on ai_workflow_agent_run (tenant_id, project_id, script_id);
create index idx_ai_workflow_agent_run_analysis_stage
  on ai_workflow_agent_run (analysis_stage_id);
alter table ai_workflow_agent_run
  add constraint fk_ai_workflow_agent_run_script foreign key (script_id) references script(id);
alter table ai_workflow_agent_run
  add constraint fk_ai_workflow_agent_run_analysis_stage foreign key (analysis_stage_id)
    references script_analysis_stage(id);
