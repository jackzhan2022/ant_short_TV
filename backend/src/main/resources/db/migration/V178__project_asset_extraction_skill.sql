alter table project add column asset_extraction_skill_code varchar(128) null;
create index idx_project_asset_extraction_skill on project(asset_extraction_skill_code);

-- Persist the effective rules for single episode retries as well as batch retries.
alter table script_ai_operation add column asset_plan_json longtext null;
alter table script_analysis_fanout_snapshot add column asset_plan_json longtext null;
