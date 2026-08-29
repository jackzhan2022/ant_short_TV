create table ai_agent_definition (
  id bigint primary key auto_increment,
  code varchar(128) not null,
  version_no int not null,
  name varchar(200) not null,
  description varchar(1000) null,
  prompt_template longtext not null,
  output_schema longtext null,
  status varchar(32) not null,
  published boolean not null default false,
  created_by bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_ai_agent_definition_version (code, version_no),
  index idx_ai_agent_definition_active (code, published, status)
);

create table ai_skill_definition (
  id bigint primary key auto_increment,
  code varchar(128) not null,
  version_no int not null,
  name varchar(200) not null,
  category varchar(64) null,
  content longtext not null,
  status varchar(32) not null,
  published boolean not null default false,
  created_by bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_ai_skill_definition_version (code, version_no),
  index idx_ai_skill_definition_active (code, published, status)
);

create table ai_agent_skill (
  agent_definition_id bigint not null,
  skill_definition_id bigint not null,
  primary key (agent_definition_id, skill_definition_id),
  constraint fk_ai_agent_skill_agent foreign key (agent_definition_id) references ai_agent_definition(id),
  constraint fk_ai_agent_skill_skill foreign key (skill_definition_id) references ai_skill_definition(id)
);

create table ai_model_parameter_profile (
  id bigint primary key auto_increment,
  model_id bigint not null,
  version_no int not null,
  temperature decimal(4,3) not null default 0.7,
  top_p decimal(4,3) null,
  max_tokens int not null default 2048,
  json_mode boolean not null default false,
  timeout_seconds int not null default 60,
  retry_count int not null default 1,
  status varchar(32) not null,
  published boolean not null default false,
  created_by bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_ai_model_parameter_profile_version (model_id, version_no),
  index idx_ai_model_parameter_profile_active (model_id, published, status),
  constraint fk_ai_model_parameter_profile_model foreign key (model_id) references ai_model(id)
);

create table script_analysis_config_snapshot (
  id bigint primary key auto_increment,
  task_id bigint not null,
  agent_code varchar(128) not null,
  agent_version_no int null,
  skill_versions_json longtext null,
  model_parameter_profile_id bigint null,
  model_parameter_version_no int null,
  snapshot_json longtext not null,
  created_at datetime not null,
  unique key uk_script_analysis_config_snapshot_task (task_id),
  constraint fk_script_analysis_config_snapshot_task foreign key (task_id) references script_analysis_task(id)
);

insert into ai_model_parameter_profile
  (model_id, version_no, temperature, top_p, max_tokens, json_mode, timeout_seconds, retry_count, status, published, created_at, updated_at)
select id, 1, 0.7, null, 2048, false, 60, 1, 'ENABLED', true, current_timestamp, current_timestamp
from ai_model
where service_type = 'TEXT'
  and not exists (select 1 from ai_model_parameter_profile p where p.model_id = ai_model.id);
