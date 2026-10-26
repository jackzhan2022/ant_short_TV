create table system_audio_asset (
  id bigint primary key auto_increment,
  name varchar(255) not null,
  category varchar(255) not null default '',
  gender varchar(16) not null default '',
  age_group varchar(16) not null default '',
  source_path varchar(1000) not null,
  content_sha256 char(64) not null,
  storage_path varchar(1000) not null,
  mime_type varchar(128) not null,
  file_size bigint not null,
  duration_seconds decimal(12,3) not null,
  status varchar(32) not null default 'ACTIVE',
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_system_audio_content (content_sha256),
  index idx_system_audio_filter (status, gender, age_group, id)
);
