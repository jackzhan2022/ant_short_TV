update storyboard_asset_reference reference_row
set resolution_status = 'RESOLVED', updated_at = now()
where reference_row.retired_at is null
  and reference_row.resolution_status = 'ASSET_PENDING'
  and exists (
    select 1 from asset_visual_variant variant_row
     where variant_row.id = reference_row.variant_id
       and variant_row.tenant_id = reference_row.tenant_id
       and variant_row.project_id = reference_row.project_id
       and variant_row.deleted_at is null
       and variant_row.generation_status = 'COMPLETED'
       and (variant_row.current_image_result_id is not null
            or (variant_row.current_image_url is not null and variant_row.current_image_url <> ''))
  );

update storyboard_asset_reference reference_row
set resolution_status = 'ASSET_PENDING', updated_at = now()
where reference_row.retired_at is null
  and reference_row.resolution_status = 'RESOLVED'
  and reference_row.variant_id is not null
  and not exists (
    select 1 from asset_visual_variant variant_row
     where variant_row.id = reference_row.variant_id
       and variant_row.tenant_id = reference_row.tenant_id
       and variant_row.project_id = reference_row.project_id
       and variant_row.deleted_at is null
       and variant_row.generation_status = 'COMPLETED'
       and (variant_row.current_image_result_id is not null
            or (variant_row.current_image_url is not null and variant_row.current_image_url <> ''))
  );
