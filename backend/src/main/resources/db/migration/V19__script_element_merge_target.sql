alter table character_asset
  add column merge_target_id bigint null;
create index idx_character_asset_merge_target on character_asset (merge_target_id);

alter table scene_asset
  add column merge_target_id bigint null;
create index idx_scene_asset_merge_target on scene_asset (merge_target_id);

alter table prop_asset
  add column merge_target_id bigint null;
create index idx_prop_asset_merge_target on prop_asset (merge_target_id);
