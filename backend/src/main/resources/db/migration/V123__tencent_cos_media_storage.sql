create table media_upload_session (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint null,
  user_id bigint not null,
  session_token varchar(64) not null,
  object_key varchar(512) not null,
  file_name varchar(255) not null,
  content_type varchar(128) not null,
  declared_size bigint null,
  verified_size bigint null,
  etag varchar(160) null,
  status varchar(32) not null,
  expires_at datetime not null,
  completed_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  constraint uk_media_upload_session_token unique (session_token),
  constraint uk_media_upload_session_object unique (object_key)
);

create index idx_media_upload_session_scope_status
  on media_upload_session (tenant_id, project_id, status, expires_at);

create table media_object (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint null,
  asset_type varchar(64) not null,
  asset_id bigint not null,
  version_id varchar(64) not null,
  rendition_type varchar(32) not null,
  object_key varchar(512) not null,
  mime_type varchar(128) not null,
  file_size bigint not null,
  etag varchar(160) null,
  checksum varchar(160) null,
  storage_class varchar(64) not null,
  width int null,
  height int null,
  status varchar(32) not null,
  error_message varchar(1000) null,
  created_at datetime not null,
  updated_at datetime not null,
  constraint uk_media_object_key unique (object_key),
  constraint uk_media_object_asset_rendition
    unique (tenant_id, asset_type, asset_id, version_id, rendition_type)
);

create index idx_media_object_scope_asset
  on media_object (tenant_id, project_id, asset_type, asset_id, version_id);

create table media_processing_job (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint null,
  media_object_id bigint not null,
  provider_job_id varchar(128) null,
  operation varchar(64) not null,
  input_key varchar(512) not null,
  output_key varchar(512) not null,
  status varchar(32) not null,
  attempt_no int not null default 1,
  error_code varchar(128) null,
  error_message varchar(1000) null,
  submitted_at datetime null,
  completed_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  constraint fk_media_processing_object foreign key (media_object_id) references media_object(id),
  constraint uk_media_processing_output_operation unique (output_key, operation),
  constraint uk_media_processing_provider_job unique (provider_job_id)
);

create index idx_media_processing_status
  on media_processing_job (status, updated_at);

create table media_delivery_grant (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint null,
  user_id bigint not null,
  resource_type varchar(64) not null,
  resource_id bigint not null,
  version_id varchar(64) not null,
  rendition_type varchar(32) not null,
  object_key_hash char(64) not null,
  expires_at datetime not null,
  revision int not null default 1,
  created_at datetime not null,
  updated_at datetime not null,
  constraint uk_media_delivery_grant_subject_object unique (user_id, object_key_hash)
);

create index idx_media_delivery_grant_scope
  on media_delivery_grant (tenant_id, project_id, resource_type, resource_id, version_id);
create index idx_media_delivery_grant_expiry
  on media_delivery_grant (expires_at);
