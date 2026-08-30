create table script_episode_version (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  episode_id bigint not null,
  version_no int not null,
  content longtext not null,
  status varchar(32) not null,
  is_current boolean not null,
  created_by bigint not null,
  created_at datetime not null,
  current_marker varchar(240) generated always as (
    case when is_current = true
      then concat(tenant_id, ':', project_id, ':', episode_id)
      else null
    end
  ),
  unique key uk_script_episode_version_no (episode_id, version_no),
  unique key uk_script_episode_version_current (current_marker),
  index idx_script_episode_version_scope (tenant_id, project_id, episode_id, created_at),
  constraint fk_script_episode_version_episode foreign key (episode_id) references script_episode(id)
);

insert into script_episode_version
  (tenant_id, project_id, episode_id, version_no, content, status, is_current, created_by, created_at)
select episode.tenant_id, episode.project_id, episode.id, 1, episode.content, 'ACTIVE', true,
       coalesce(script.created_by, 0), episode.created_at
  from script_episode episode
  join script on script.id = episode.script_id
 where episode.retired_at is null
   and not exists (
     select 1 from script_episode_version version where version.episode_id = episode.id
   );
