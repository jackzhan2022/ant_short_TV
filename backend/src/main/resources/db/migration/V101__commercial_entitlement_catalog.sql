create table commercial_entitlement_definition (
  id bigint primary key auto_increment,
  code varchar(48) not null,
  name varchar(128) not null,
  description varchar(500) null,
  category varchar(16) not null,
  status varchar(16) not null,
  sort_order int not null default 0,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_commercial_entitlement_definition_code (code),
  index idx_commercial_entitlement_definition_list (status, sort_order, id)
);

insert ignore into commercial_entitlement_definition
  (code, name, description, category, status, sort_order, created_at, updated_at)
values
  ('ONE_TIME_POINTS', '一次性积分', '购买后一次性发放的团队积分', 'SYSTEM', 'ACTIVE', 10, now(), now()),
  ('PERIODIC_POINTS', '周期积分', '订阅生效后按周期发放的团队积分', 'SYSTEM', 'ACTIVE', 20, now(), now()),
  ('GLOBAL_DISCOUNT', '全局折扣', '订阅有效期内适用于 AI 积分结算的全局折扣', 'SYSTEM', 'ACTIVE', 30, now(), now());
