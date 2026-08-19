create table team_point_account (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  balance int not null,
  total_granted int not null,
  total_consumed int not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_team_point_account_tenant (tenant_id)
);

create table team_point_transaction (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  user_id bigint not null,
  transaction_type varchar(32) not null,
  change_amount int not null,
  balance_after int not null,
  business_scene varchar(100) null,
  business_id bigint null,
  description varchar(500) null,
  created_at datetime not null,
  index idx_team_point_transaction_tenant_created (tenant_id, created_at),
  index idx_team_point_transaction_scene (business_scene, business_id)
);
