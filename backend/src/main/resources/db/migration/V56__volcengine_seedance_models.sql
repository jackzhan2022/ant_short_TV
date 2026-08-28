insert ignore into ai_provider
  (name, code, supported_types, default_base_url, recommended_models, description, status, created_at, updated_at)
values
  ('火山方舟', 'VOLCENGINE_ARK', 'VIDEO', 'https://ark.cn-beijing.volces.com/api/v3',
   'Seedance 2.0 Fast,Seedance 2.0 Standard,Seedance 2.5',
   '火山方舟 Seedance 视频生成模型', 'DISABLED', now(), now());

insert ignore into ai_provider_config
  (provider_id, api_key_cipher, base_url, extra_config, status, last_test_status, created_at, updated_at)
select id, null, 'https://ark.cn-beijing.volces.com/api/v3', null, 'DISABLED', 'UNTESTED', now(), now()
  from ai_provider
 where code = 'VOLCENGINE_ARK';

insert ignore into ai_model
  (provider_id, code, name, model_code, service_type, description, status, is_default, sort, config_json, created_at, updated_at)
select id, 'SEEDANCE_2_0_FAST', 'Seedance 2.0 Fast', '__SEEDANCE_2_0_FAST_ENDPOINT_ID__', 'VIDEO',
       '火山方舟 Seedance 2.0 Fast，Endpoint ID 待配置', 'DISABLED', false, 320, null, now(), now()
  from ai_provider
 where code = 'VOLCENGINE_ARK';

insert ignore into ai_model
  (provider_id, code, name, model_code, service_type, description, status, is_default, sort, config_json, created_at, updated_at)
select id, 'SEEDANCE_2_0_STANDARD', 'Seedance 2.0 Standard', '__SEEDANCE_2_0_STANDARD_ENDPOINT_ID__', 'VIDEO',
       '火山方舟 Seedance 2.0 Standard，Endpoint ID 待配置', 'DISABLED', false, 310, null, now(), now()
  from ai_provider
 where code = 'VOLCENGINE_ARK';

insert ignore into ai_model
  (provider_id, code, name, model_code, service_type, description, status, is_default, sort, config_json, created_at, updated_at)
select id, 'SEEDANCE_2_5', 'Seedance 2.5', '__SEEDANCE_2_5_ENDPOINT_ID__', 'VIDEO',
       '火山方舟 Seedance 2.5，Endpoint ID 待配置', 'DISABLED', false, 300, null, now(), now()
  from ai_provider
 where code = 'VOLCENGINE_ARK';

insert ignore into ai_model_capability
  (model_id, capability, status, config_json, created_at, updated_at)
select model.id, 'VIDEO_GENERATION', 'DISABLED', null, now(), now()
  from ai_model model
 where model.code in ('SEEDANCE_2_0_FAST', 'SEEDANCE_2_0_STANDARD', 'SEEDANCE_2_5');
