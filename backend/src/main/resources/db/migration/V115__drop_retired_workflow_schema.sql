-- V114 has atomically retired old work and released unspent reservations.
-- Shared execution, attempts, workflow runs, formal assets and financial audit remain.
drop table review_semantic_decision;
drop table review_candidate_audit;
drop table review_pipeline_stage;
drop table review_issue_event;
drop table review_issue_hit;
drop table review_batch_repair;
drop table review_issue;
drop table script_asset_promotion_decision;
drop table script_asset_candidate_alias;
drop table script_asset_candidate;
drop table script_asset_normalization_run;
drop table ai_agent_skill;
drop table ai_agent_definition;
drop table ai_skill_definition;

drop index idx_script_analysis_task_pipeline on script_analysis_task;
alter table script_analysis_task drop column pipeline_version;
alter table script_episode drop column summary;
alter table script_analysis_config_snapshot drop column agent_code;
alter table script_analysis_config_snapshot drop column agent_version_no;
alter table script_analysis_config_snapshot drop column skill_versions_json;
alter table script_analysis_config_snapshot drop column model_parameter_profile_id;
alter table script_analysis_config_snapshot drop column model_parameter_version_no;
alter table review_task drop column result_format;
alter table review_task drop column result_json;
alter table review_task drop column global_index_json;
alter table review_unit_result drop column coverage_json;
alter table review_unit_result drop column candidates_json;
alter table review_fanout_unit rename column candidate_saved to report_saved;
alter table review_fanout_unit alter column stage_type set default 'DIMENSION_MARKDOWN';
