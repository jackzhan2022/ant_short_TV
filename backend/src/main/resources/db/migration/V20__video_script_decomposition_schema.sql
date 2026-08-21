create table video_decomposition_batch (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  name varchar(200) not null,
  model_id bigint null,
  status varchar(32) not null,
  total_episodes int not null,
  completed_episodes int not null,
  failed_episodes int not null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  index idx_video_decomposition_batch_tenant_project (tenant_id, project_id),
  index idx_video_decomposition_batch_tenant_created (tenant_id, created_at),
  index idx_video_decomposition_batch_status (status)
);

create table video_decomposition_episode (
  id bigint primary key auto_increment,
  batch_id bigint not null,
  tenant_id bigint not null,
  project_id bigint not null,
  episode_no int not null,
  source_file_name varchar(500) not null,
  storage_path varchar(1000) not null,
  mime_type varchar(128) null,
  file_size bigint not null,
  duration_seconds decimal(10,2) null,
  status varchar(32) not null,
  analysis_version int not null,
  draft_content longtext null,
  draft_status varchar(32) null,
  draft_version int not null,
  confirmed_script_version_id bigint null,
  error_code varchar(64) null,
  error_message varchar(1000) null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_video_decomposition_episode_no (batch_id, episode_no),
  index idx_video_decomposition_episode_tenant_project (tenant_id, project_id),
  index idx_video_decomposition_episode_batch_status (batch_id, status)
);

create table video_decomposition_analysis (
  id bigint primary key auto_increment,
  episode_id bigint not null,
  schema_version varchar(32) not null,
  status varchar(32) not null,
  raw_response longtext null,
  normalized_json longtext null,
  provider_request_id varchar(128) null,
  ai_call_log_id bigint null,
  created_at datetime not null,
  index idx_video_decomposition_analysis_episode (episode_id, created_at),
  index idx_video_decomposition_analysis_status (status)
);

create table video_decomposition_attempt (
  id bigint primary key auto_increment,
  episode_id bigint not null,
  attempt_no int not null,
  phase varchar(32) not null,
  status varchar(32) not null,
  provider_request_id varchar(128) null,
  ai_call_log_id bigint null,
  error_code varchar(64) null,
  error_message varchar(1000) null,
  started_at datetime not null,
  finished_at datetime null,
  unique key uk_video_decomposition_attempt_no (episode_id, attempt_no, phase),
  index idx_video_decomposition_attempt_episode (episode_id, started_at)
);
