create table project_audio_asset (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  name varchar(255) not null,
  upload_session_token varchar(64) not null,
  storage_path varchar(1000) not null,
  mime_type varchar(128) not null,
  file_size bigint not null,
  duration_seconds decimal(12,3) not null,
  status varchar(32) not null default 'ACTIVE',
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_project_audio_upload (tenant_id, project_id, upload_session_token),
  index idx_project_audio_assets (tenant_id, project_id, status)
);

create table character_voice_reference (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  character_id bigint not null,
  source_project_id bigint not null,
  resource_type varchar(40) not null,
  resource_id bigint not null,
  updated_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_character_voice (tenant_id, project_id, character_id)
);
