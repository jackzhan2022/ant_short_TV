create table episode_compose_task (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  episode_no int not null,
  task_name varchar(200) not null,
  compose_config text null,
  storyboard_count int not null,
  total_duration_seconds decimal(10,2) null,
  status varchar(32) not null,
  error_message text null,
  started_at datetime null,
  completed_at datetime null,
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  deleted_at datetime null,
  index idx_episode_compose_task_project_status (tenant_id, project_id, status),
  index idx_episode_compose_task_episode (tenant_id, project_id, episode_no)
);

create table episode_compose_item (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  task_id bigint not null,
  episode_no int not null,
  storyboard_id bigint not null,
  storyboard_order int not null,
  shot_result_id bigint null,
  video_url varchar(1000) null,
  duration_seconds decimal(8,2) null,
  width int null,
  height int null,
  status varchar(32) not null,
  error_message text null,
  created_at datetime not null,
  index idx_episode_compose_item_task (task_id),
  index idx_episode_compose_item_episode (tenant_id, project_id, episode_no)
);

create table episode_video_version (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  episode_no int not null,
  compose_task_id bigint not null,
  version_no int not null,
  version_name varchar(200) not null,
  video_url varchar(1000) not null,
  storage_path varchar(1000) not null,
  cover_url varchar(1000) null,
  duration_seconds decimal(10,2) null,
  width int null,
  height int null,
  file_size bigint null,
  format varchar(32) null,
  material_id bigint null,
  is_current boolean not null,
  status varchar(32) not null,
  current_marker varchar(100) generated always as (
    case when is_current = true and status = 'ACTIVE'
      then concat(tenant_id, ':', project_id, ':', episode_no)
      else null
    end
  ),
  created_by bigint not null,
  created_at datetime not null,
  updated_at datetime not null,
  index idx_episode_video_version_episode (tenant_id, project_id, episode_no),
  index idx_episode_video_version_task (compose_task_id),
  unique key uk_episode_video_version_current (current_marker)
);

create table episode_export_record (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  episode_no int not null,
  video_version_id bigint not null,
  export_type varchar(32) not null,
  export_status varchar(32) not null,
  file_name varchar(255) null,
  file_size bigint null,
  download_url varchar(1000) null,
  error_message text null,
  created_by bigint not null,
  created_at datetime not null,
  index idx_episode_export_record_episode (tenant_id, project_id, episode_no),
  index idx_episode_export_record_version (video_version_id)
);

insert ignore into permission
  (code, name, type, resource, action, created_at, updated_at)
values
  ('EPISODE_COMPOSE:VIEW', '查看单集合成任务', 'PAGE', 'EPISODE_COMPOSE', 'VIEW', now(), now()),
  ('EPISODE_COMPOSE:CREATE', '创建单集合成任务', 'BUTTON', 'EPISODE_COMPOSE', 'CREATE', now(), now()),
  ('EPISODE_COMPOSE:CANCEL', '取消单集合成任务', 'BUTTON', 'EPISODE_COMPOSE', 'CANCEL', now(), now()),
  ('EPISODE_COMPOSE:DELETE', '删除单集合成记录', 'BUTTON', 'EPISODE_COMPOSE', 'DELETE', now(), now()),
  ('EPISODE_VERSION:VIEW', '查看成片版本', 'PAGE', 'EPISODE_VERSION', 'VIEW', now(), now()),
  ('EPISODE_VERSION:SET_CURRENT', '设置当前成片版本', 'BUTTON', 'EPISODE_VERSION', 'SET_CURRENT', now(), now()),
  ('EPISODE_VERSION:DOWNLOAD', '下载成片', 'BUTTON', 'EPISODE_VERSION', 'DOWNLOAD', now(), now()),
  ('EPISODE_VERSION:DELETE', '删除成片版本', 'BUTTON', 'EPISODE_VERSION', 'DELETE', now(), now()),
  ('EPISODE_VERSION:SAVE_MATERIAL', '保存成片素材', 'BUTTON', 'EPISODE_VERSION', 'SAVE_MATERIAL', now(), now());

insert ignore into project_role_permission (tenant_id, project_id, role_id, permission_id, created_at)
select pr.tenant_id, pr.project_id, pr.id, p.id, now()
  from project_role pr
  join permission p on p.code in (
    'EPISODE_COMPOSE:VIEW',
    'EPISODE_COMPOSE:CREATE',
    'EPISODE_COMPOSE:CANCEL',
    'EPISODE_COMPOSE:DELETE',
    'EPISODE_VERSION:VIEW',
    'EPISODE_VERSION:SET_CURRENT',
    'EPISODE_VERSION:DOWNLOAD',
    'EPISODE_VERSION:DELETE',
    'EPISODE_VERSION:SAVE_MATERIAL'
  )
 where pr.code = 'PROJECT_OWNER'
   and not exists (
     select 1
       from project_role_permission existing
      where existing.tenant_id = pr.tenant_id
        and existing.project_id = pr.project_id
        and existing.role_id = pr.id
        and existing.permission_id = p.id
   );
