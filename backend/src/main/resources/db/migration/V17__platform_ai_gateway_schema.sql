create table ai_provider_config (
  id bigint primary key auto_increment,
  provider_id bigint not null,
  api_key_cipher text null,
  base_url varchar(512) null,
  extra_config text null,
  status varchar(32) not null,
  last_test_status varchar(32) not null,
  last_test_message varchar(500) null,
  last_test_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_ai_provider_config_provider (provider_id),
  index idx_ai_provider_config_status (status)
);

create table ai_model (
  id bigint primary key auto_increment,
  provider_id bigint not null,
  code varchar(128) not null,
  name varchar(128) not null,
  model_code varchar(256) not null,
  service_type varchar(32) not null,
  description varchar(1000) null,
  status varchar(32) not null,
  is_default boolean not null,
  sort int not null,
  config_json text null,
  legacy_service_config_id bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  default_marker varchar(32) generated always as (
    case
      when is_default = true and status = 'ENABLED' then 'DEFAULT'
      else null
    end
  ),
  unique key uk_ai_model_code (code),
  index idx_ai_model_provider_model_type (provider_id, model_code, service_type),
  unique key uk_ai_model_default_type (service_type, default_marker),
  index idx_ai_model_provider_status (provider_id, status),
  index idx_ai_model_legacy_service_config (legacy_service_config_id)
);

create table ai_model_capability (
  id bigint primary key auto_increment,
  model_id bigint not null,
  capability varchar(64) not null,
  status varchar(32) not null,
  config_json text null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_ai_model_capability (model_id, capability),
  index idx_ai_model_capability_status (capability, status)
);

create table project_ai_config (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  text_model_id bigint null,
  image_model_id bigint null,
  video_model_id bigint null,
  audio_model_id bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_project_ai_config_project (tenant_id, project_id),
  index idx_project_ai_config_tenant (tenant_id)
);

alter table ai_call_log
  add column task_id bigint null;
alter table ai_call_log
  add column model_id bigint null;
alter table ai_call_log
  add column provider_id bigint null;
alter table ai_call_log
  add column trace_id varchar(128) null;
alter table ai_call_log
  add column provider_request_id varchar(128) null;
alter table ai_call_log
  add column prompt_tokens int null;
alter table ai_call_log
  add column completion_tokens int null;
alter table ai_call_log
  add column total_tokens int null;
alter table ai_call_log
  add column estimated_cost decimal(18,6) null;

alter table ai_image_task
  add column model_id bigint null;

insert into ai_provider_config
  (provider_id, api_key_cipher, base_url, extra_config, status, last_test_status, created_at, updated_at)
select p.id, null, p.default_base_url, null, p.status, 'UNTESTED', now(), now()
  from ai_provider p
 where not exists (
   select 1 from ai_provider_config c where c.provider_id = p.id
 );

insert into ai_model
  (provider_id, code, name, model_code, service_type, description, status, is_default, sort, config_json, legacy_service_config_id, created_at, updated_at)
select
  p.id,
  concat(upper(replace(replace(c.provider, ' ', '_'), '-', '_')), '_', c.service_type, '_', c.id),
  c.name,
  c.model,
  c.service_type,
  c.remark,
  case when c.enabled = true and p.status = 'ENABLED' then 'ENABLED' else 'DISABLED' end,
  c.is_default,
  c.priority,
  null,
  c.id,
  now(),
  now()
 from ai_service_config c
 join ai_provider p on p.code = c.provider
where c.deleted_at is null
  and not exists (
    select 1 from ai_model m where m.legacy_service_config_id = c.id
  );

insert into ai_model_capability
  (model_id, capability, status, config_json, created_at, updated_at)
select
  m.id,
  case m.service_type
    when 'TEXT' then 'TEXT_GENERATION'
    when 'IMAGE' then 'IMAGE_GENERATION'
    when 'VIDEO' then 'VIDEO_GENERATION'
    when 'VOICE' then 'AUDIO_GENERATION'
    else concat(m.service_type, '_GENERATION')
  end,
  m.status,
  null,
  now(),
  now()
 from ai_model m
where not exists (
  select 1 from ai_model_capability c where c.model_id = m.id
);
