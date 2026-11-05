create table desktop_source_project (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  connection_id varchar(80) not null,
  source_team_id varchar(100) not null,
  source_project_id varchar(100) not null,
  source_script_id varchar(100) not null,
  name varchar(200) not null,
  target_project_id bigint null,
  active boolean not null default true,
  synced_at datetime not null,
  unique key uk_desktop_source_project (tenant_id,connection_id,source_team_id,source_project_id,source_script_id)
);

create table desktop_source_asset (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  source_project_row_id bigint not null,
  source_asset_id varchar(100) not null,
  source_parent_id varchar(100) null,
  name varchar(200) not null,
  asset_type varchar(16) not null,
  active boolean not null default true,
  target_asset_id bigint null,
  target_variant_id bigint null,
  target_name varchar(100) null,
  target_parent_name varchar(100) null,
  match_status varchar(20) not null default 'UNBOUND',
  unique key uk_desktop_source_asset (tenant_id,source_project_row_id,source_asset_id),
  index idx_desktop_source_asset_project (tenant_id,source_project_row_id)
);
