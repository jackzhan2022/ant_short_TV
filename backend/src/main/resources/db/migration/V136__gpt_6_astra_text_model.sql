insert ignore into ai_model
  (provider_id, code, name, model_code, service_type, description, status, is_default,
   sort, config_json, created_at, updated_at)
select id, 'gpt-6-astra', 'GPT-6 Astra（中度思考）', 'gpt-6-astra', 'TEXT',
       'GPT-6 Astra 文本模型，使用中度思考（reasoning_effort=medium）', 'ENABLED', false,
       100, '{"reasoningEffort":"medium"}', now(), now()
  from ai_provider
 where id = coalesce(
   (select provider_id from ai_model where code = 'gpt-5.6-terra-xiongxiong'),
   (select id from ai_provider where code = 'OpenAI')
 );

insert ignore into ai_model_capability
  (model_id, capability, status, config_json, created_at, updated_at)
select model.id, capability.code, 'ENABLED', null, now(), now()
  from ai_model model
 cross join (select 'TEXT_GENERATION' as code union all select 'TOOL_CALLING') capability
 where model.code = 'gpt-6-astra';

insert into ai_model_parameter_profile
  (model_id, version_no, temperature, top_p, max_tokens, json_mode, timeout_seconds,
   retry_count, status, published, created_at, updated_at)
select id, 1, 0.7, null, 2048, false, 60, 1, 'ENABLED', true, now(), now()
  from ai_model model
 where model.code = 'gpt-6-astra'
   and not exists (select 1 from ai_model_parameter_profile profile where profile.model_id = model.id);
