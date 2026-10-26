create table script_manual_asset_request (
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  request_id varchar(36) not null,
  fingerprint varchar(64) not null,
  asset_type varchar(32) not null,
  asset_id bigint not null,
  variant_id bigint not null,
  created_at datetime not null,
  primary key (tenant_id, project_id, request_id)
);
