create table ai_model_point_price_version (
  id bigint primary key auto_increment,
  model_id bigint not null,
  version_no int not null,
  status varchar(32) not null,
  effective_from datetime not null,
  effective_to datetime null,
  published_at datetime null,
  created_by bigint null,
  created_at datetime not null,
  constraint uk_ai_model_point_price_version unique (model_id, version_no),
  index idx_ai_model_point_price_effective (model_id, status, effective_from, effective_to)
);

create table ai_model_point_price_component (
  id bigint primary key auto_increment,
  price_version_id bigint not null,
  metric varchar(32) not null,
  unit_size decimal(24,8) not null,
  point_rate decimal(24,8) not null,
  dimensions_json text null,
  dimensions_key varchar(500) not null default '',
  created_at datetime not null,
  constraint fk_ai_model_point_price_component_version foreign key (price_version_id) references ai_model_point_price_version(id),
  constraint uk_ai_model_point_price_component unique (price_version_id, metric, dimensions_key),
  index idx_ai_model_point_price_component_match (price_version_id, metric)
);

alter table ai_execution_task add column cost_price_version_id bigint null;
alter table ai_execution_task add column point_price_version_id bigint null;
alter table ai_point_reservation add column point_price_version_id bigint null;
alter table ai_point_reservation modify column policy_version_id bigint null;

create index idx_ai_execution_billing_versions
  on ai_execution_task (cost_price_version_id, point_price_version_id);

insert into platform_permission (code, name, resource, action, created_at, updated_at)
values ('PLATFORM_AI_POINT_PRICE_PUBLISH', 'Publish model point price versions', 'PLATFORM_AI_POINT_PRICE', 'PUBLISH', now(), now());

insert into platform_role_permission (role_id, permission_id, created_at)
select role.id, permission.id, now()
  from platform_role role
  join platform_permission permission on permission.code = 'PLATFORM_AI_POINT_PRICE_PUBLISH'
 where role.code = 'PLATFORM_ADMIN';
