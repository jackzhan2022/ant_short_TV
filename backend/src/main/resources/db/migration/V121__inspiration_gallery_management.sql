alter table inspiration_creation add column prompt_text longtext null;
alter table inspiration_creation add column tags_json text null;
alter table inspiration_creation add column publish_status varchar(32) not null default 'PUBLISHED';
alter table inspiration_creation add column source_type varchar(32) not null default 'IMPORTED';
alter table inspiration_creation add column deleted_at datetime null;

update inspiration_creation
   set publish_status = 'PUBLISHED',
       source_type = 'IMPORTED'
 where publish_status is null
    or source_type is null;

create index idx_inspiration_creation_public_order
  on inspiration_creation (publish_status, deleted_at, import_status, sort_order, id);

create index idx_inspiration_creation_management
  on inspiration_creation (deleted_at, publish_status, creation_type, sort_order, id);

insert into platform_permission (code, name, resource, action, created_at, updated_at)
select 'PLATFORM_INSPIRATION_MANAGE',
       'Manage inspiration gallery',
       'PLATFORM_INSPIRATION',
       'MANAGE',
       now(),
       now()
where not exists (
  select 1 from platform_permission where code = 'PLATFORM_INSPIRATION_MANAGE'
);

insert into platform_role_permission (role_id, permission_id, created_at)
select role.id, permission.id, now()
  from platform_role role
  join platform_permission permission
    on permission.code = 'PLATFORM_INSPIRATION_MANAGE'
 where role.code = 'PLATFORM_ADMIN'
   and not exists (
     select 1
       from platform_role_permission role_permission
      where role_permission.role_id = role.id
        and role_permission.permission_id = permission.id
   );
