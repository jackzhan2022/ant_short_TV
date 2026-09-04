alter table storyboard add column episode_id bigint null;
alter table storyboard add column storyboard_no int null;
alter table storyboard add column shot_plan_json longtext null;
alter table storyboard add column prompt_document_json longtext null;
alter table storyboard add column source_fingerprint varchar(128) null;
alter table storyboard add column generated_by_run_id bigint null;
alter table storyboard add column material_binding_status varchar(32) not null default 'LEGACY';

update storyboard set storyboard_no = shot_no where storyboard_no is null;

create index idx_storyboard_active_episode_order
  on storyboard (tenant_id, project_id, episode_id, storyboard_no, deleted_at);
create index idx_storyboard_generated_run
  on storyboard (generated_by_run_id);
