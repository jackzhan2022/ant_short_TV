alter table ai_image_task
  add column ai_call_log_id bigint null;

alter table character_asset
  add column main_image_url varchar(1000) null;

alter table character_asset
  add column main_image_result_id bigint null;

alter table scene_asset
  add column main_image_url varchar(1000) null;

alter table scene_asset
  add column main_image_result_id bigint null;

alter table storyboard
  add column first_frame_image_url varchar(1000) null;

alter table storyboard
  add column first_frame_result_id bigint null;
