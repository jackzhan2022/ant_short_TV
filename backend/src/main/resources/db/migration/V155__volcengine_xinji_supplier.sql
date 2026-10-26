insert into ai_provider
  (name, code, supported_types, default_base_url, recommended_models, description, status, created_at, updated_at)
select '火山（信计）', 'VOLCENGINE_ARK_XINJI', source.supported_types, source.default_base_url,
       source.recommended_models, source.description, source.status, now(), now()
  from ai_provider source
 where source.code = 'VOLCENGINE_ARK'
   and not exists (select 1 from ai_provider target where target.code = 'VOLCENGINE_ARK_XINJI');

insert into ai_provider_config
  (provider_id, api_key_cipher, base_url, extra_config, status, last_test_status, last_test_message,
   last_test_at, created_at, updated_at)
select target.id, config.api_key_cipher, config.base_url, config.extra_config, config.status,
       config.last_test_status, config.last_test_message, config.last_test_at, now(), now()
  from ai_provider source
  join ai_provider_config config on config.provider_id = source.id
  join ai_provider target on target.code = 'VOLCENGINE_ARK_XINJI'
 where source.code = 'VOLCENGINE_ARK'
   and not exists (select 1 from ai_provider_config existing where existing.provider_id = target.id);

update ai_model
   set provider_id = (select id from ai_provider where code = 'VOLCENGINE_ARK_XINJI')
 where code in ('SEEDANCE_2_0_STANDARD', 'SEEDANCE_2_5')
   and provider_id = (select id from ai_provider where code = 'VOLCENGINE_ARK')
   and exists (select 1 from ai_provider where code = 'VOLCENGINE_ARK_XINJI');
