alter table library_drama add column cover_url varchar(1024) null;
alter table library_drama add column category varchar(40) null;
alter table library_drama add column completion_status varchar(16) not null default 'ONGOING';
alter table library_drama add column total_episodes int null;
alter table library_episode add column source_upload_hash varchar(64) null;
alter table library_episode add column poster_url varchar(1024) null;
alter table library_episode add column duration_seconds double null;
alter table library_episode add column width int null;
alter table library_episode add column height int null;
alter table miniapp_drama_release add column cover_url varchar(1024) null;
alter table miniapp_drama_release add column category varchar(40) null;
alter table miniapp_drama_release add column completion_status varchar(16) not null default 'ONGOING';
alter table miniapp_drama_release add column total_episodes int null;
alter table miniapp_drama_release add column sort_order int not null default 0;
alter table miniapp_drama_release add column recommended boolean not null default false;
alter table miniapp_drama_release add column episode_count int not null default 0;
alter table miniapp_release_episode add column poster_url varchar(1024) null;
alter table miniapp_release_episode add column duration_seconds double null;
alter table miniapp_release_episode add column width int null;
alter table miniapp_release_episode add column height int null;
update miniapp_drama_release r set episode_count = (select count(*) from miniapp_release_episode e where e.release_id=r.id);
create index idx_miniapp_catalog_order on miniapp_drama_release (miniapp_id, published, sort_order, id);
create table miniapp_watch_progress (
  id bigint primary key auto_increment,
  miniapp_id bigint not null,
  openid varchar(128) not null,
  drama_id bigint not null,
  episode_id bigint not null,
  position_seconds double not null default 0,
  duration_seconds double not null default 0,
  completed boolean not null default false,
  client_sequence bigint not null default 0,
  playback_session varchar(64) not null,
  updated_at datetime not null,
  unique key uk_watch_viewer_drama (miniapp_id, openid, drama_id),
  index idx_watch_viewer_recent (miniapp_id, openid, updated_at),
  constraint fk_watch_miniapp foreign key (miniapp_id) references team_miniapp(id)
);
