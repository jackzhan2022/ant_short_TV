alter table ai_agent_skill
  add column sort_order int not null default 100;

update ai_agent_skill
   set sort_order = case
     when skill_definition_id in (select id from ai_skill_definition where code = 'strict-json-output') then 10
     when skill_definition_id in (select id from ai_skill_definition where code = 'no-invention') then 20
     when skill_definition_id in (select id from ai_skill_definition where code = 'stable-entity-naming') then 30
     when skill_definition_id in (select id from ai_skill_definition where code = 'short-drama-structure') then 40
     else 100
   end;

update ai_agent_definition
   set output_schema = '{"type":"object","additionalProperties":false,"required":["characters","scenes","props"],"properties":{"characters":{"type":"array","maxItems":500,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":100},"aliases":{"type":"array","items":{"type":"string","maxLength":100}},"roleType":{"type":"string","enum":["LEAD","SUPPORTING","MINOR","OTHER"],"default":"SUPPORTING"},"gender":{"type":"string","maxLength":32},"ageRange":{"type":"string","maxLength":32},"identity":{"type":"string","maxLength":200},"personality":{"type":"array","items":{"type":"string","maxLength":100}},"appearance":{"type":"string","maxLength":500},"prompt":{"type":"string","maxLength":4000}}}},"scenes":{"type":"array","maxItems":500,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":100},"aliases":{"type":"array","items":{"type":"string","maxLength":100}},"sceneType":{"type":"string","enum":["INTERIOR","EXTERIOR","MIXED","OTHER"],"default":"INTERIOR"},"atmosphere":{"type":"string","maxLength":100},"description":{"type":"string","maxLength":4000},"visualStyle":{"type":"string","maxLength":300},"prompt":{"type":"string","maxLength":4000}}}},"props":{"type":"array","maxItems":500,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":100},"aliases":{"type":"array","items":{"type":"string","maxLength":100}},"propType":{"type":"string","enum":["KEY_PROP","DAILY","WEAPON","DOCUMENT","OTHER"],"default":"KEY_PROP"},"appearance":{"type":"string","maxLength":500},"plotFunction":{"type":"string","maxLength":500},"relatedCharacter":{"type":"string","maxLength":200},"prompt":{"type":"string","maxLength":4000}}}}}}',
       updated_at = now()
 where code = 'script-character-scene-recognition'
   and version_no = 1
   and published = true
   and output_schema = '{"characters":[],"scenes":[],"props":[]}';
