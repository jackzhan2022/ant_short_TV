create table storyboard_character_voice (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  storyboard_id bigint not null,
  character_id bigint not null,
  variant_id bigint not null default 0,
  source_project_id bigint not null,
  resource_type varchar(40) not null,
  resource_id bigint not null,
  updated_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_storyboard_character_voice (tenant_id,project_id,storyboard_id,character_id,variant_id)
);
