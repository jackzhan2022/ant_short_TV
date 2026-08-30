create table ai_workflow_agent (
  id bigint primary key auto_increment,
  code varchar(128) not null,
  name varchar(200) not null,
  description varchar(1000) null,
  system_prompt longtext not null,
  model_id bigint not null,
  temperature decimal(4,3) not null default 0.7,
  max_tokens int not null default 4096,
  max_steps int not null default 20,
  status varchar(32) not null,
  revision bigint not null default 0,
  created_by bigint null,
  updated_by bigint null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_ai_workflow_agent_code (code),
  index idx_ai_workflow_agent_status (status),
  index idx_ai_workflow_agent_model (model_id),
  constraint fk_ai_workflow_agent_model foreign key (model_id) references ai_model(id)
);

create table ai_workflow_agent_skill (
  agent_id bigint not null,
  skill_code varchar(128) not null,
  load_order int not null,
  created_at datetime not null,
  primary key (agent_id, skill_code),
  unique key uk_ai_workflow_agent_skill_order (agent_id, load_order),
  index idx_ai_workflow_agent_skill_code (skill_code),
  constraint fk_ai_workflow_agent_skill_agent foreign key (agent_id) references ai_workflow_agent(id)
);

create table ai_workflow_agent_tool (
  agent_id bigint not null,
  tool_code varchar(128) not null,
  created_at datetime not null,
  primary key (agent_id, tool_code),
  index idx_ai_workflow_agent_tool_code (tool_code),
  constraint fk_ai_workflow_agent_tool_agent foreign key (agent_id) references ai_workflow_agent(id)
);

create table ai_workflow_agent_run (
  id bigint primary key auto_increment,
  agent_id bigint null,
  agent_code varchar(128) not null,
  run_type varchar(32) not null,
  tenant_id bigint null,
  user_id bigint not null,
  project_id bigint null,
  episode_id bigint null,
  task_id bigint null,
  status varchar(32) not null,
  model_id bigint not null,
  temperature decimal(4,3) not null,
  max_tokens int not null,
  max_steps int not null,
  prompt_snapshot longtext not null,
  skill_snapshot_json longtext null,
  tool_codes_json longtext null,
  final_output longtext null,
  error_code varchar(128) null,
  error_message varchar(2000) null,
  started_at datetime not null,
  finished_at datetime null,
  created_at datetime not null,
  index idx_ai_workflow_agent_run_agent_created (agent_code, created_at),
  index idx_ai_workflow_agent_run_scope (tenant_id, project_id, episode_id),
  index idx_ai_workflow_agent_run_status (status, created_at),
  constraint fk_ai_workflow_agent_run_agent foreign key (agent_id) references ai_workflow_agent(id) on delete set null,
  constraint fk_ai_workflow_agent_run_model foreign key (model_id) references ai_model(id)
);

create table ai_workflow_agent_run_step (
  id bigint primary key auto_increment,
  run_id bigint not null,
  step_no int not null,
  step_type varchar(32) not null,
  status varchar(32) not null,
  ai_call_log_id bigint null,
  tool_code varchar(128) null,
  input_json longtext null,
  output_json longtext null,
  error_code varchar(128) null,
  error_message varchar(2000) null,
  started_at datetime not null,
  finished_at datetime null,
  created_at datetime not null,
  unique key uk_ai_workflow_agent_run_step (run_id, step_no),
  index idx_ai_workflow_agent_run_step_tool (tool_code),
  constraint fk_ai_workflow_agent_run_step_run foreign key (run_id) references ai_workflow_agent_run(id)
);

insert into ai_model_capability (model_id, capability, status, created_at, updated_at)
select model.id, 'TOOL_CALLING', model.status, current_timestamp, current_timestamp
  from ai_model model
 where model.service_type = 'TEXT'
   and not exists (
     select 1
       from ai_model_capability capability
      where capability.model_id = model.id
        and capability.capability = 'TOOL_CALLING'
   );

insert into platform_permission (code, name, resource, action, created_at, updated_at)
select 'PLATFORM_AI_WORKFLOW_AGENT_VIEW', 'View workflow agents', 'PLATFORM_AI_WORKFLOW_AGENT', 'VIEW', current_timestamp, current_timestamp
where not exists (select 1 from platform_permission where code = 'PLATFORM_AI_WORKFLOW_AGENT_VIEW');
insert into platform_permission (code, name, resource, action, created_at, updated_at)
select 'PLATFORM_AI_WORKFLOW_AGENT_EDIT', 'Edit workflow agents', 'PLATFORM_AI_WORKFLOW_AGENT', 'EDIT', current_timestamp, current_timestamp
where not exists (select 1 from platform_permission where code = 'PLATFORM_AI_WORKFLOW_AGENT_EDIT');
insert into platform_permission (code, name, resource, action, created_at, updated_at)
select 'PLATFORM_AI_WORKFLOW_SKILL_VIEW', 'View workflow skills', 'PLATFORM_AI_WORKFLOW_SKILL', 'VIEW', current_timestamp, current_timestamp
where not exists (select 1 from platform_permission where code = 'PLATFORM_AI_WORKFLOW_SKILL_VIEW');
insert into platform_permission (code, name, resource, action, created_at, updated_at)
select 'PLATFORM_AI_WORKFLOW_SKILL_EDIT', 'Edit workflow skills', 'PLATFORM_AI_WORKFLOW_SKILL', 'EDIT', current_timestamp, current_timestamp
where not exists (select 1 from platform_permission where code = 'PLATFORM_AI_WORKFLOW_SKILL_EDIT');

insert into platform_role_permission (role_id, permission_id, created_at)
select role.id, permission.id, current_timestamp
  from platform_role role
  join platform_permission permission on permission.code in (
    'PLATFORM_AI_WORKFLOW_AGENT_VIEW', 'PLATFORM_AI_WORKFLOW_AGENT_EDIT',
    'PLATFORM_AI_WORKFLOW_SKILL_VIEW', 'PLATFORM_AI_WORKFLOW_SKILL_EDIT'
  )
 where role.code = 'PLATFORM_ADMIN'
   and not exists (
     select 1
       from platform_role_permission existing
      where existing.role_id = role.id
        and existing.permission_id = permission.id
   );
