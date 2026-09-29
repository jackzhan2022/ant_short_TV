create table storyboard_asset_reference (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  storyboard_id bigint not null,
  asset_type varchar(32) not null,
  asset_id bigint null,
  variant_id bigint null,
  reference_role varchar(32) not null,
  sort_order int not null,
  resolution_status varchar(32) not null,
  source_type varchar(32) not null,
  source_name varchar(200) null,
  locked_by_user boolean not null default false,
  generated_by_run_id bigint null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  retired_at datetime null,
  active_order_marker varchar(220) generated always as (
    case when retired_at is null
      then concat(storyboard_id, ':', asset_type, ':', sort_order)
      else null
    end
  ),
  unique key uk_storyboard_asset_reference_order (active_order_marker),
  index idx_storyboard_asset_reference_project
    (tenant_id, project_id, asset_type, asset_id, retired_at),
  index idx_storyboard_asset_reference_storyboard
    (tenant_id, project_id, storyboard_id, retired_at, asset_type, sort_order),
  constraint fk_storyboard_asset_reference_storyboard foreign key (storyboard_id)
    references storyboard(id),
  constraint fk_storyboard_asset_reference_variant foreign key (variant_id)
    references asset_visual_variant(id)
);

insert into platform_permission (code,name,resource,action,created_at,updated_at)
select 'PLATFORM_STORYBOARD_BINDING_BACKFILL','Backfill storyboard asset bindings',
       'PLATFORM_STORYBOARD','BINDING_BACKFILL',now(),now()
where not exists (
  select 1 from platform_permission where code='PLATFORM_STORYBOARD_BINDING_BACKFILL'
);

insert into platform_role_permission (role_id,permission_id,created_at)
select role.id,permission.id,now()
  from platform_role role
  join platform_permission permission
    on permission.code='PLATFORM_STORYBOARD_BINDING_BACKFILL'
 where role.code='PLATFORM_ADMIN'
   and not exists (
     select 1 from platform_role_permission role_permission
      where role_permission.role_id=role.id and role_permission.permission_id=permission.id
   );
