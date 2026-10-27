create table ark_asset_scope (
  scope_key varchar(128) primary key,
  lease_token varchar(64) null,
  lease_until timestamp null,
  admitted_recovery_id bigint null,
  next_create_at timestamp null,
  last_inventory_at timestamp null,
  asset_count int not null default 0,
  group_count int not null default 0,
  updated_at timestamp not null default current_timestamp
);
create table ark_managed_resource (
  id bigint primary key auto_increment,
  scope_key varchar(128) not null,
  provider_project varchar(255) not null,
  resource_type varchar(16) not null,
  owner_key varchar(191) not null,
  provider_id varchar(255) null,
  provider_group_id varchar(255) null,
  provider_name varchar(128) not null,
  tenant_id bigint null,
  project_id bigint null,
  source_storage_path text null,
  source_url text null,
  status varchar(32) not null default 'NEW',
  error_message varchar(1000) null,
  deletion_previous_status varchar(32) null,
  last_used_at timestamp not null default current_timestamp,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique(scope_key,provider_project,resource_type,owner_key)
);
create index idx_ark_resource_provider on ark_managed_resource(scope_key,resource_type,provider_id);
create table seedance_avatar_recovery (
  id bigint primary key auto_increment,
  source_task_id bigint not null unique,
  tenant_id bigint not null,
  project_id bigint not null,
  storyboard_id bigint not null,
  created_by bigint not null,
  status varchar(32) not null default 'PENDING',
  phase varchar(128) not null default '等待素材入库',
  prepared_snapshot_json longtext null,
  retry_task_id bigint null,
  error_message varchar(1000) null,
  claim_token varchar(64) null,
  claim_until timestamp null,
  next_poll_at timestamp null,
  deadline_at timestamp not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp
);
create index idx_avatar_recovery_due on seedance_avatar_recovery(status,next_poll_at,claim_until);
create table ark_asset_consumer (
  resource_id bigint not null,
  consumer_key varchar(128) not null,
  video_task_id bigint null,
  recovery_id bigint null,
  created_at timestamp not null default current_timestamp,
  primary key(resource_id,consumer_key)
);
alter table ai_video_task add column avatar_recovery_source_task_id bigint null;
create unique index uk_video_avatar_recovery_source on ai_video_task(avatar_recovery_source_task_id);

alter table virtual_avatar_task add column ingestion_attempt int not null default 0;
