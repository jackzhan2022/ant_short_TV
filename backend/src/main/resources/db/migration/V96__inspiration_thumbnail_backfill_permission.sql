insert into platform_permission (code, name, resource, action, created_at, updated_at)
select 'PLATFORM_INSPIRATION_THUMBNAIL_BACKFILL',
       'Backfill inspiration thumbnails',
       'PLATFORM_INSPIRATION',
       'THUMBNAIL_BACKFILL',
       now(),
       now()
where not exists (
  select 1 from platform_permission where code = 'PLATFORM_INSPIRATION_THUMBNAIL_BACKFILL'
);

insert into platform_role_permission (role_id, permission_id, created_at)
select role.id, permission.id, now()
  from platform_role role
  join platform_permission permission
    on permission.code = 'PLATFORM_INSPIRATION_THUMBNAIL_BACKFILL'
 where role.code = 'PLATFORM_ADMIN'
   and not exists (
     select 1
       from platform_role_permission role_permission
      where role_permission.role_id = role.id
        and role_permission.permission_id = permission.id
   );
