create table script_episode_summary (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  episode_id bigint not null,
  schema_version int not null,
  content_json longtext not null,
  source varchar(32) not null,
  generated_by_run_id bigint null,
  created_by bigint not null,
  updated_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_script_episode_summary_episode (tenant_id, episode_id),
  index idx_script_episode_summary_script (tenant_id, project_id, script_id),
  index idx_script_episode_summary_run (generated_by_run_id),
  constraint fk_script_episode_summary_project foreign key (project_id) references project(id),
  constraint fk_script_episode_summary_script foreign key (script_id) references script(id),
  constraint fk_script_episode_summary_episode foreign key (episode_id) references script_episode(id),
  constraint fk_script_episode_summary_run foreign key (generated_by_run_id)
    references ai_workflow_agent_run(id) on delete set null
);

alter table script_episode
  modify column script_version_id bigint null;
alter table script_episode
  add column generated_by_run_id bigint null;
create index idx_script_episode_generated_run
  on script_episode (generated_by_run_id);
alter table script_episode
  add constraint fk_script_episode_generated_run foreign key (generated_by_run_id)
    references ai_workflow_agent_run(id) on delete set null;

alter table character_asset
  add column script_id bigint null;
alter table character_asset
  add column normalized_name varchar(100) null;
alter table character_asset
  add column content_json longtext null;
alter table character_asset
  add column source varchar(32) null;
alter table character_asset
  add column generated_by_run_id bigint null;
create index idx_character_asset_script_name
  on character_asset (tenant_id, project_id, script_id, normalized_name, deleted_at);
alter table character_asset
  add constraint fk_character_asset_script foreign key (script_id) references script(id);
alter table character_asset
  add constraint fk_character_asset_generated_run foreign key (generated_by_run_id)
    references ai_workflow_agent_run(id) on delete set null;

alter table scene_asset
  add column script_id bigint null;
alter table scene_asset
  add column normalized_name varchar(100) null;
alter table scene_asset
  add column content_json longtext null;
alter table scene_asset
  add column source varchar(32) null;
alter table scene_asset
  add column generated_by_run_id bigint null;
create index idx_scene_asset_script_name
  on scene_asset (tenant_id, project_id, script_id, normalized_name, deleted_at);
alter table scene_asset
  add constraint fk_scene_asset_script foreign key (script_id) references script(id);
alter table scene_asset
  add constraint fk_scene_asset_generated_run foreign key (generated_by_run_id)
    references ai_workflow_agent_run(id) on delete set null;

alter table prop_asset
  add column script_id bigint null;
alter table prop_asset
  add column normalized_name varchar(100) null;
alter table prop_asset
  add column content_json longtext null;
alter table prop_asset
  add column source varchar(32) null;
alter table prop_asset
  add column generated_by_run_id bigint null;
create index idx_prop_asset_script_name
  on prop_asset (tenant_id, project_id, script_id, normalized_name, deleted_at);
alter table prop_asset
  add constraint fk_prop_asset_script foreign key (script_id) references script(id);
alter table prop_asset
  add constraint fk_prop_asset_generated_run foreign key (generated_by_run_id)
    references ai_workflow_agent_run(id) on delete set null;

alter table asset_visual_variant
  add column content_json longtext null;
alter table asset_visual_variant
  add column generated_by_run_id bigint null;
create index idx_asset_visual_variant_run
  on asset_visual_variant (generated_by_run_id);
alter table asset_visual_variant
  add constraint fk_asset_visual_variant_run foreign key (generated_by_run_id)
    references ai_workflow_agent_run(id) on delete set null;

alter table asset_visual_variant_episode
  add column generated_by_run_id bigint null;
create index idx_asset_visual_variant_episode_run
  on asset_visual_variant_episode (generated_by_run_id);
alter table asset_visual_variant_episode
  add constraint fk_asset_visual_variant_episode_run foreign key (generated_by_run_id)
    references ai_workflow_agent_run(id) on delete set null;
