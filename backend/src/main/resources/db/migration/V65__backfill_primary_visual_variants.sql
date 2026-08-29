insert into asset_visual_variant
  (tenant_id, project_id, asset_type, asset_id, name, appearance, prompt, source_type,
   generation_status, current_image_result_id, current_image_url, is_primary, created_by, created_at, updated_at)
select tenant_id, project_id, 'CHARACTER', id, '默认形象', appearance, prompt, 'LEGACY_BACKFILL',
       'COMPLETED', main_image_result_id, main_image_url, true, created_by, now(), now()
  from character_asset
 where deleted_at is null
   and (main_image_result_id is not null or main_image_url is not null)
   and not exists (
       select 1 from asset_visual_variant v
        where v.tenant_id = character_asset.tenant_id and v.project_id = character_asset.project_id
          and v.asset_type = 'CHARACTER' and v.asset_id = character_asset.id and v.deleted_at is null
   );

insert into asset_visual_variant
  (tenant_id, project_id, asset_type, asset_id, name, appearance, prompt, source_type,
   generation_status, current_image_result_id, current_image_url, is_primary, created_by, created_at, updated_at)
select tenant_id, project_id, 'SCENE', id, '默认形象', description, prompt, 'LEGACY_BACKFILL',
       'COMPLETED', main_image_result_id, main_image_url, true, created_by, now(), now()
  from scene_asset
 where deleted_at is null
   and (main_image_result_id is not null or main_image_url is not null)
   and not exists (
       select 1 from asset_visual_variant v
        where v.tenant_id = scene_asset.tenant_id and v.project_id = scene_asset.project_id
          and v.asset_type = 'SCENE' and v.asset_id = scene_asset.id and v.deleted_at is null
   );

insert into asset_visual_variant
  (tenant_id, project_id, asset_type, asset_id, name, appearance, prompt, source_type,
   generation_status, current_image_result_id, current_image_url, is_primary, created_by, created_at, updated_at)
select tenant_id, project_id, 'PROP', id, '默认形象', appearance, prompt, 'LEGACY_BACKFILL',
       'COMPLETED', main_image_result_id, main_image_url, true, created_by, now(), now()
  from prop_asset
 where deleted_at is null
   and (main_image_result_id is not null or main_image_url is not null)
   and not exists (
       select 1 from asset_visual_variant v
        where v.tenant_id = prop_asset.tenant_id and v.project_id = prop_asset.project_id
          and v.asset_type = 'PROP' and v.asset_id = prop_asset.id and v.deleted_at is null
   );
