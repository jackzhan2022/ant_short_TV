-- Preflight: scripts/sql/asset-extraction-duplicate-preflight.sql. Never merge conflicts implicitly.
create table script_asset_extraction_owner (
 tenant_id bigint not null, project_id bigint not null, script_id bigint not null,
 execution_id bigint null, execution_version int null, attempt_id bigint null,
 request_fingerprint varchar(64) null, updated_at datetime not null,
 primary key (tenant_id,project_id,script_id)
);
alter table character_asset
 add column active_identity_name varchar(100) generated always as
 (case when deleted_at is null then nullif(trim(normalized_name),'') else null end);
create unique index uk_character_active_identity on character_asset (tenant_id,project_id,script_id,active_identity_name);
alter table scene_asset
 add column active_identity_name varchar(100) generated always as
 (case when deleted_at is null then nullif(trim(normalized_name),'') else null end);
create unique index uk_scene_active_identity on scene_asset (tenant_id,project_id,script_id,active_identity_name);
alter table prop_asset
 add column active_identity_name varchar(100) generated always as
 (case when deleted_at is null then nullif(trim(normalized_name),'') else null end);
create unique index uk_prop_active_identity on prop_asset (tenant_id,project_id,script_id,active_identity_name);
