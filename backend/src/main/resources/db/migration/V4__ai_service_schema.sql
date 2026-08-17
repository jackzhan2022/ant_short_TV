create table ai_provider (
  id bigint primary key auto_increment,
  name varchar(100) not null,
  code varchar(50) not null,
  supported_types varchar(200) not null,
  default_base_url varchar(500) null,
  recommended_models text null,
  description varchar(500) null,
  status varchar(32) not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_ai_provider_code (code),
  index idx_ai_provider_status (status)
);

create table ai_service_config (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  provider varchar(50) not null,
  service_type varchar(32) not null,
  name varchar(100) not null,
  base_url varchar(500) not null,
  api_key_cipher text not null,
  model varchar(200) not null,
  endpoint varchar(300) null,
  query_endpoint varchar(300) null,
  priority int not null,
  is_default boolean not null,
  enabled boolean not null,
  last_test_status varchar(32) not null,
  last_test_message varchar(500) null,
  last_test_at datetime null,
  remark varchar(500) null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  default_marker varchar(32) generated always as (
    case
      when is_default = true and deleted_at is null then 'DEFAULT'
      else null
    end
  ),
  index idx_ai_service_config_tenant_type (tenant_id, service_type),
  index idx_ai_service_config_tenant_provider (tenant_id, provider),
  index idx_ai_service_config_tenant_status (tenant_id, enabled),
  unique key uk_ai_service_config_default (tenant_id, service_type, default_marker)
);

create table ai_service_test_log (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  service_config_id bigint not null,
  provider varchar(50) not null,
  service_type varchar(32) not null,
  model varchar(200) not null,
  test_status varchar(32) not null,
  message varchar(500) null,
  duration_ms bigint not null,
  created_by bigint not null,
  created_at datetime not null,
  index idx_ai_service_test_log_config (service_config_id),
  index idx_ai_service_test_log_tenant_created (tenant_id, created_at)
);

create table ai_call_log (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  user_id bigint not null,
  service_config_id bigint null,
  provider varchar(50) null,
  service_type varchar(32) not null,
  model varchar(200) null,
  business_scene varchar(100) not null,
  request_summary text null,
  response_summary text null,
  status varchar(32) not null,
  error_message varchar(500) null,
  duration_ms bigint not null,
  created_at datetime not null,
  index idx_ai_call_log_tenant_created (tenant_id, created_at),
  index idx_ai_call_log_service_config (service_config_id)
);

insert into ai_provider
  (name, code, supported_types, default_base_url, recommended_models, description, status, created_at, updated_at)
values
  ('OpenAI', 'OpenAI', 'TEXT,IMAGE,VOICE', 'https://api.openai.com/v1', 'gpt-4.1-mini,gpt-4.1,o4-mini', '支持 OpenAI 官方接口及 OpenAI Compatible 接口', 'ENABLED', now(), now()),
  ('Gemini', 'Gemini', 'TEXT,IMAGE', 'https://generativelanguage.googleapis.com', 'gemini-2.5-pro,gemini-2.5-flash', '支持 Gemini 文本、图片与多模态能力', 'ENABLED', now(), now()),
  ('火山', '火山', 'TEXT,IMAGE,VIDEO,VOICE', 'https://ark.cn-beijing.volces.com/api/v3', 'doubao-seed-1.6,seedance-1.0-pro', '支持豆包、火山引擎相关模型能力', 'ENABLED', now(), now()),
  ('MiniMax', 'MiniMax', 'TEXT,VOICE,VIDEO', 'https://api.minimax.chat/v1', 'abab6.5s-chat,speech-2.6-hd', '支持文本生成、语音合成及后续视频能力', 'ENABLED', now(), now());
