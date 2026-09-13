create table script_asset_extraction_coordination (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  owner_execution_id bigint null,
  owner_execution_version int null,
  owner_attempt bigint null,
  source_fingerprint varchar(128) null,
  state varchar(32) not null default 'IDLE',
  created_at datetime not null,
  updated_at datetime not null,
  released_at datetime null,
  constraint uk_script_asset_extraction_coordination
    unique (tenant_id, project_id, script_id)
);
