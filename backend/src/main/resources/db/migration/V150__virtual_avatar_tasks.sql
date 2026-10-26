create table virtual_avatar_task (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  variant_id bigint not null,
  character_id bigint not null,
  title varchar(255) not null,
  source_image_result_id bigint null,
  source_image_url text not null,
  source_storage_path text null,
  provider_project varchar(255) not null,
  provider_group_id varchar(255) null,
  provider_asset_id varchar(255) null,
  status varchar(32) not null default 'QUEUED',
  phase varchar(64) not null default '等待提交',
  progress int not null default 0,
  retryable boolean not null default false,
  error_code varchar(128) null,
  error_message varchar(1000) null,
  result_storage_path text null,
  execution_id bigint null,
  claim_token varchar(64) null,
  claimed_at timestamp null,
  next_poll_at timestamp null,
  poll_deadline_at timestamp null,
  created_by bigint not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  completed_at timestamp null
);
create index idx_avatar_due on virtual_avatar_task(status,next_poll_at,claimed_at);
create index idx_avatar_variant on virtual_avatar_task(tenant_id,project_id,variant_id,id);
create index idx_avatar_tasks on virtual_avatar_task(tenant_id,created_by,created_at);
