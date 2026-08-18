alter table storyboard add column current_voice_result_id bigint null;
alter table storyboard add column current_audio_url varchar(1000) null;
alter table storyboard add column current_subtitle_id bigint null;
alter table storyboard add column current_subtitle_url varchar(1000) null;
alter table storyboard add column current_shot_result_id bigint null;
alter table storyboard add column current_shot_video_url varchar(1000) null;

create table ai_voice_task (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  storyboard_id bigint not null,
  service_config_id bigint not null,
  provider_code varchar(64) not null,
  model varchar(128) not null,
  voice_type varchar(32) not null,
  speaker_name varchar(100) null,
  voice_id varchar(128) not null,
  text_content text not null,
  speed decimal(4,2) not null,
  pitch decimal(4,2) not null,
  volume decimal(4,2) not null,
  status varchar(32) not null,
  error_message text null,
  started_at datetime null,
  completed_at datetime null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  index idx_ai_voice_task_project_status (tenant_id, project_id, status),
  index idx_ai_voice_task_storyboard (tenant_id, project_id, storyboard_id)
);

create table ai_voice_result (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  task_id bigint not null,
  storyboard_id bigint not null,
  audio_url varchar(1000) not null,
  storage_path varchar(1000) not null,
  duration_seconds decimal(8,2) null,
  file_size bigint null,
  format varchar(32) null,
  material_id bigint null,
  is_selected boolean not null,
  status varchar(32) not null,
  created_at datetime not null,
  updated_at datetime not null,
  index idx_ai_voice_result_task (task_id),
  index idx_ai_voice_result_storyboard (tenant_id, project_id, storyboard_id)
);

create table storyboard_subtitle (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  storyboard_id bigint not null,
  voice_result_id bigint null,
  subtitle_type varchar(32) not null,
  content text not null,
  srt_url varchar(1000) not null,
  style_config text null,
  is_selected boolean not null,
  status varchar(32) not null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  index idx_storyboard_subtitle_storyboard (tenant_id, project_id, storyboard_id)
);

create table shot_compose_task (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  storyboard_id bigint not null,
  voice_result_id bigint null,
  subtitle_id bigint null,
  compose_config text null,
  status varchar(32) not null,
  error_message text null,
  started_at datetime null,
  completed_at datetime null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  index idx_shot_compose_task_project_status (tenant_id, project_id, status),
  index idx_shot_compose_task_storyboard (tenant_id, project_id, storyboard_id)
);

create table shot_compose_result (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  task_id bigint not null,
  storyboard_id bigint not null,
  video_url varchar(1000) not null,
  storage_path varchar(1000) not null,
  cover_url varchar(1000) null,
  duration_seconds decimal(8,2) null,
  width int null,
  height int null,
  file_size bigint null,
  format varchar(32) null,
  material_id bigint null,
  is_selected boolean not null,
  status varchar(32) not null,
  created_at datetime not null,
  updated_at datetime not null,
  index idx_shot_compose_result_task (task_id),
  index idx_shot_compose_result_storyboard (tenant_id, project_id, storyboard_id)
);
