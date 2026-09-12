-- Read-only preflight. No DDL/DML and no automatic deduplication.
-- Canonical identity conflicts under the same normalization used by stored rows.
select 'CHARACTER' as asset_type, tenant_id, project_id, script_id, normalized_name,
       count(*) as duplicate_count, group_concat(id order by id) as asset_ids
from character_asset
where deleted_at is null and script_id is not null
  and normalized_name is not null and trim(normalized_name) <> ''
group by tenant_id, project_id, script_id, normalized_name having count(*) > 1
union all
select 'SCENE', tenant_id, project_id, script_id, normalized_name,
       count(*), group_concat(id order by id)
from scene_asset
where deleted_at is null and script_id is not null
  and normalized_name is not null and trim(normalized_name) <> ''
group by tenant_id, project_id, script_id, normalized_name having count(*) > 1
union all
select 'PROP', tenant_id, project_id, script_id, normalized_name,
       count(*), group_concat(id order by id)
from prop_asset
where deleted_at is null and script_id is not null
  and normalized_name is not null and trim(normalized_name) <> ''
group by tenant_id, project_id, script_id, normalized_name having count(*) > 1;

select 'CHARACTER' as asset_type, count(*) as missing_normalized_names
from character_asset where deleted_at is null and script_id is not null
  and (normalized_name is null or trim(normalized_name) = '')
union all
select 'SCENE', count(*) from scene_asset where deleted_at is null and script_id is not null
  and (normalized_name is null or trim(normalized_name) = '')
union all
select 'PROP', count(*) from prop_asset where deleted_at is null and script_id is not null
  and (normalized_name is null or trim(normalized_name) = '');

-- Repeated display names are candidates for inspection, not proof of equal semantics.
select tenant_id, project_id, asset_type, asset_id, name,
       count(*) as candidate_count, group_concat(id order by id) as variant_ids
from asset_visual_variant where deleted_at is null
group by tenant_id, project_id, asset_type, asset_id, name having count(*) > 1;
