alter table ai_video_result add column processing_type varchar(32) null;
alter table ai_video_result add column source_result_id bigint null;
alter table ai_video_result add column processing_method varchar(16) null;
alter table ai_video_result add column processing_quality varchar(16) null;

create table subtitle_erase_task (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  storyboard_id bigint not null,
  source_result_id bigint not null,
  execution_id bigint null,
  result_id bigint null,
  method varchar(16) not null,
  quality varchar(16) not null,
  region_json text null,
  client_key varchar(128) not null,
  retry_key varchar(128) null,
  provider_task_id varchar(255) null,
  provider_failed boolean not null default false,
  generation int not null default 1,
  input_url text null,
  output_url text null,
  output_expires_at timestamp null,
  output_duration decimal(12,3) null,
  output_resolution varchar(32) null,
  deadline_at timestamp not null,
  created_by bigint not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_subtitle_erase_request (tenant_id, created_by, client_key),
  unique key uk_subtitle_erase_execution (execution_id),
  index idx_subtitle_erase_storyboard (tenant_id, project_id, storyboard_id, id),
  index idx_subtitle_erase_source (tenant_id, project_id, source_result_id, id)
);
