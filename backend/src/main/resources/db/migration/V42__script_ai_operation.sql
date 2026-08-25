create table script_ai_operation (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  operation_type varchar(64) not null,
  script_id bigint null,
  script_version_id bigint null,
  redacted_input_json longtext not null,
  idempotency_key varchar(200) not null,
  status varchar(32) not null,
  execution_id bigint null,
  result_type varchar(64) null,
  result_id bigint null,
  error_code varchar(64) null,
  error_message varchar(1000) null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  completed_at datetime null,
  constraint uk_script_ai_operation_idempotency
    unique (tenant_id, operation_type, idempotency_key),
  constraint fk_script_ai_operation_execution
    foreign key (execution_id) references ai_execution_task(id),
  index idx_script_ai_operation_execution (execution_id),
  index idx_script_ai_operation_project (tenant_id, project_id, created_at)
);

alter table script_version add column execution_id bigint null;
create unique index uk_script_version_execution on script_version (execution_id);

insert into ai_point_policy_version
  (scene, model_id, capability, version_no, status, effective_from, effective_to,
   charge_provider_rejection, charge_provider_billed_failure, charge_timeout,
   charge_business_failure, created_at, published_at)
values
  ('script_generate', null, 'TEXT', 1, 'PUBLISHED', '2000-01-01 00:00:00', null, false, true, true, true, now(), now()),
  ('script_rewrite', null, 'TEXT', 1, 'PUBLISHED', '2000-01-01 00:00:00', null, false, true, true, true, now(), now()),
  ('script_element_extract', null, 'TEXT', 1, 'PUBLISHED', '2000-01-01 00:00:00', null, false, true, true, true, now(), now()),
  ('character_extract', null, 'TEXT', 1, 'PUBLISHED', '2000-01-01 00:00:00', null, false, true, true, true, now(), now()),
  ('scene_extract', null, 'TEXT', 1, 'PUBLISHED', '2000-01-01 00:00:00', null, false, true, true, true, now(), now()),
  ('prop_extract', null, 'TEXT', 1, 'PUBLISHED', '2000-01-01 00:00:00', null, false, true, true, true, now(), now()),
  ('storyboard_breakdown', null, 'TEXT', 1, 'PUBLISHED', '2000-01-01 00:00:00', null, false, true, true, true, now(), now()),
  ('prompt_generate', null, 'TEXT', 1, 'PUBLISHED', '2000-01-01 00:00:00', null, false, true, true, true, now(), now());

insert into ai_point_policy_component
  (policy_version_id, metric, unit_size, point_rate, dimensions_json, dimensions_key, created_at)
select id, 'FIXED_EXECUTION', 1, 1, '{}', '', now()
  from ai_point_policy_version
 where scene in (
   'script_generate', 'script_rewrite', 'script_element_extract',
   'character_extract', 'scene_extract', 'prop_extract',
   'storyboard_breakdown', 'prompt_generate'
 ) and version_no = 1;
