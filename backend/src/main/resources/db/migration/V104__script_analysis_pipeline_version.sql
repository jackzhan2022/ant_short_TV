alter table script_analysis_task
  add column pipeline_version varchar(32) not null default 'LEGACY_V1' after workflow_code;

create index idx_script_analysis_task_pipeline
  on script_analysis_task (tenant_id, pipeline_version, status);
