alter table asset_visual_variant
  add column virtual_avatar_asset_id varchar(255) null;

create index idx_asset_visual_variant_virtual_avatar
  on asset_visual_variant (tenant_id, project_id, virtual_avatar_asset_id);
