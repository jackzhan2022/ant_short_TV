update project_ai_config
   set text_model_id = null
 where text_model_id in (select id from ai_model where legacy_service_config_id is not null);
update project_ai_config
   set image_model_id = null
 where image_model_id in (select id from ai_model where legacy_service_config_id is not null);
update project_ai_config
   set video_model_id = null
 where video_model_id in (select id from ai_model where legacy_service_config_id is not null);
update project_ai_config
   set audio_model_id = null
 where audio_model_id in (select id from ai_model where legacy_service_config_id is not null);

update ai_execution_task
   set requested_model_id = null
 where requested_model_id in (select id from ai_model where legacy_service_config_id is not null);
update ai_execution_task
   set resolved_model_id = null
 where resolved_model_id in (select id from ai_model where legacy_service_config_id is not null);
update ai_execution_attempt
   set model_id = null
 where model_id in (select id from ai_model where legacy_service_config_id is not null);
update ai_call_log
   set model_id = null
 where model_id in (select id from ai_model where legacy_service_config_id is not null);
update ai_image_task
   set model_id = null
 where model_id in (select id from ai_model where legacy_service_config_id is not null);
update video_decomposition_batch
   set model_id = null
 where model_id in (select id from ai_model where legacy_service_config_id is not null);
update ai_point_policy_version
   set model_id = null
 where model_id in (select id from ai_model where legacy_service_config_id is not null);

delete from ai_usage_cost_line
 where model_id in (select id from ai_model where legacy_service_config_id is not null);
delete from ai_usage_line
 where model_id in (select id from ai_model where legacy_service_config_id is not null);
delete from ai_model_price_component
 where price_version_id in (
   select price.id
     from ai_model_price_version price
     join ai_model model on model.id = price.model_id
    where model.legacy_service_config_id is not null
 );
delete from ai_model_price_version
 where model_id in (select id from ai_model where legacy_service_config_id is not null);
delete from ai_model_capability
 where model_id in (select id from ai_model where legacy_service_config_id is not null);
delete from ai_model
 where legacy_service_config_id is not null;

update ai_provider_config
   set api_key_cipher = null,
       status = 'DISABLED',
       last_test_status = 'UNTESTED',
       last_test_message = null,
       last_test_at = null,
       updated_at = now();

alter table ai_video_task add column model_id bigint null;

alter table ai_call_log drop index idx_ai_call_log_service_config;
alter table ai_call_log drop column service_config_id;
alter table ai_image_task drop column service_config_id;
alter table ai_video_task drop column service_config_id;
alter table ai_voice_task drop column service_config_id;

alter table ai_model drop index idx_ai_model_legacy_service_config;
alter table ai_model drop column legacy_service_config_id;

drop table ai_service_test_log;
drop table ai_service_config;

delete from project_role_permission
 where permission_id in (
   select id from permission
    where code in ('AI_SERVICE:VIEW', 'AI_SERVICE:CREATE', 'AI_SERVICE:EDIT', 'AI_SERVICE:DELETE', 'AI_SERVICE:TEST')
 );
delete from role_permission
 where permission_id in (
   select id from permission
    where code in ('AI_SERVICE:VIEW', 'AI_SERVICE:CREATE', 'AI_SERVICE:EDIT', 'AI_SERVICE:DELETE', 'AI_SERVICE:TEST')
 );
delete from permission
 where code in ('AI_SERVICE:VIEW', 'AI_SERVICE:CREATE', 'AI_SERVICE:EDIT', 'AI_SERVICE:DELETE', 'AI_SERVICE:TEST');

insert ignore into permission
  (code, name, type, resource, action, created_at, updated_at)
values
  ('AI_CALL_LOG:VIEW', '查看AI调用日志', 'PAGE', 'AI_CALL_LOG', 'VIEW', now(), now());

insert ignore into role_permission (role_id, permission_id, created_at)
select role.id, permission.id, now()
  from `role` role
  join permission on permission.code = 'AI_CALL_LOG:VIEW'
 where role.code in ('OWNER', 'ADMIN')
   and role.deleted_at is null;
