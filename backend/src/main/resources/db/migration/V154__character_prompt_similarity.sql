create table character_prompt_similarity_check (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  script_version_id bigint not null,
  execution_id bigint not null,
  input_fingerprint varchar(64) not null,
  input_json longtext not null,
  result_json longtext null,
  created_by bigint not null,
  created_at datetime not null,
  completed_at datetime null,
  unique key uk_character_similarity_execution (execution_id),
  index idx_character_similarity_project (tenant_id, project_id, script_id, id)
);
