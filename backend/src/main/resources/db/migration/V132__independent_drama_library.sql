create table library_drama (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  title varchar(200) not null,
  description text null,
  deleted_at datetime null,
  created_at datetime not null,
  index idx_library_drama_team (tenant_id, deleted_at, id)
);

create table library_episode (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  drama_id bigint not null,
  episode_no int not null,
  title varchar(200) not null,
  source_type varchar(32) not null,
  source_project_id bigint null,
  source_version_id bigint null,
  storage_path varchar(1024) not null,
  file_size bigint not null,
  deleted_at datetime null,
  created_at datetime not null,
  constraint fk_library_episode_drama foreign key (drama_id) references library_drama(id),
  index idx_library_episode_drama (tenant_id, drama_id, deleted_at, episode_no),
  index idx_library_episode_source (tenant_id, source_version_id, deleted_at)
);

create table team_miniapp (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  appid varchar(32) not null unique,
  name varchar(100) not null,
  binding_type varchar(32) not null default 'MANUAL',
  enabled boolean not null default true,
  created_at datetime not null,
  index idx_team_miniapp_team (tenant_id, id)
);

create table miniapp_drama_release (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  miniapp_id bigint not null,
  drama_id bigint not null,
  title varchar(200) not null,
  description text null,
  published boolean not null,
  created_at datetime not null,
  constraint fk_release_miniapp foreign key (miniapp_id) references team_miniapp(id),
  constraint fk_release_drama foreign key (drama_id) references library_drama(id),
  index idx_release_catalog (tenant_id, miniapp_id, drama_id, published)
);

create table miniapp_release_episode (
  id bigint primary key auto_increment,
  release_id bigint not null,
  episode_id bigint not null,
  episode_no int not null,
  title varchar(200) not null,
  storage_path varchar(1024) not null,
  source_version_id bigint null,
  constraint fk_release_episode_release foreign key (release_id) references miniapp_drama_release(id),
  constraint fk_release_episode_source foreign key (episode_id) references library_episode(id),
  unique key uk_release_episode (release_id, episode_id),
  unique key uk_release_episode_no (release_id, episode_no)
);

create table miniapp_viewer_session (
  id bigint primary key auto_increment,
  miniapp_id bigint not null,
  token_hash varchar(64) not null unique,
  openid varchar(128) not null,
  expires_at datetime not null,
  constraint fk_viewer_miniapp foreign key (miniapp_id) references team_miniapp(id),
  index idx_viewer_expiry (expires_at)
);

insert into permission (code, name, type, resource, action, created_at, updated_at) values
  ('DRAMA:VIEW', '查看独立剧库', 'PAGE', 'DRAMA', 'VIEW', now(), now()),
  ('DRAMA:MANAGE', '管理独立剧库', 'BUTTON', 'DRAMA', 'MANAGE', now(), now()),
  ('DRAMA:PUBLISH', '发布小程序剧集', 'BUTTON', 'DRAMA', 'PUBLISH', now(), now()),
  ('MINIAPP:MANAGE', '手工绑定团队小程序', 'BUTTON', 'MINIAPP', 'MANAGE', now(), now());

insert into role_permission (role_id, permission_id, created_at)
select r.id, p.id, now() from `role` r join permission p
  on p.code in ('DRAMA:VIEW', 'DRAMA:MANAGE', 'DRAMA:PUBLISH', 'MINIAPP:MANAGE')
where r.code in ('OWNER', 'ADMIN') and r.deleted_at is null;
