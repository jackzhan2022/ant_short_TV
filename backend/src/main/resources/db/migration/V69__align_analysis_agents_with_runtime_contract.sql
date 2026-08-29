-- Repair only untouched initial analysis Agent revisions. Later administrator
-- revisions are intentionally excluded by the exact prompt guards.
update ai_agent_definition
set prompt_template = concat(
      '你是中文短剧结构分析助手。请基于剧本输出严格 JSON：\n',
      '{"logline":"","themes":[],"characters":[],"relationships":[],"coreConflict":"","turningPoints":[],"endingHook":""}\n',
      '剧本内容：\n', '$', '{scriptContent}'),
    output_schema = '{"logline":"","themes":[],"characters":[],"relationships":[],"coreConflict":"","turningPoints":[],"endingHook":""}'
where code = 'script-global-understanding' and version_no = 1
  and prompt_template = concat('你是中文短剧结构分析助手。请基于剧本输出严格 JSON。剧本内容：', '$', '{scriptContent}');

update ai_agent_definition
set prompt_template = concat(
      '你是短剧分集助手。请根据剧情理解判断分集边界，只返回严格 JSON：\n',
      '{"episodes":[{"title":"","startMarker":"","endMarker":""}]}\n',
      '重要规则：\n1. 只返回每段标题、原文中的 startMarker 和 endMarker，不要返回 content。\n',
      '2. startMarker 和 endMarker 必须是原剧本中逐字出现的连续短句，后端会据此截取正文。\n',
      '3. 按剧情顺序返回分段；后端会自动编号，标题统一使用“第N集”。\n',
      '4. “3-2 夜 外 深海”等是第3集内部场次，不是第4集。\n',
      '5. 必须覆盖原剧本全部正文；无法判断边界时返回空 episodes，后端会将全文作为第1集。\n',
      '剧情理解：\n', '$', '{globalUnderstanding}', '\n原剧本：\n', '$', '{scriptContent}'),
    output_schema = '{"episodes":[{"title":"","startMarker":"","endMarker":""}]}'
where code = 'script-episode-split' and version_no = 1
  and prompt_template = concat('你是短剧分集助手。请根据剧情理解和原剧本智能拆分剧集，只返回严格 JSON。剧情理解：', '$', '{globalUnderstanding}', ' 原剧本：', '$', '{scriptContent}');

update ai_agent_definition
set prompt_template = concat(
      '你是短剧概要提炼助手。请为输入的每一集返回严格 JSON：\n',
      '{"episodes":[{"episodeNo":1,"summary":"","highlights":[],"endingHook":""}]}\n',
      '分集内容：\n', '$', '{episodes}'),
    output_schema = '{"episodes":[{"episodeNo":1,"summary":"","highlights":[],"endingHook":""}]}'
where code = 'script-episode-summary' and version_no = 1
  and prompt_template = concat('你是短剧概要提炼助手。请为输入的每一集返回严格 JSON。分集内容：', '$', '{episodes}');

update ai_agent_definition
set prompt_template = concat(
      '你是短剧资产识别助手。请仅基于剧本返回严格 JSON：\n',
      '{"characters":[],"scenes":[],"props":[]}\n',
      '只能返回以上三个顶层字段，禁止返回 locations、costumes、creatures、vehicles、visual_effects、organizations 或任何其他字段；场景信息必须放在 scenes 数组中。\n',
      '剧本内容：\n', '$', '{scriptContent}'),
    output_schema = '{"type":"object","additionalProperties":false,"required":["characters","scenes","props"],"properties":{"characters":{"type":"array","maxItems":500,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":100},"aliases":{"type":"array","items":{"type":"string","maxLength":100}},"roleType":{"type":"string","enum":["LEAD","SUPPORTING","MINOR","OTHER"],"default":"SUPPORTING"},"gender":{"type":"string","maxLength":32},"ageRange":{"type":"string","maxLength":32},"identity":{"type":"string","maxLength":200},"personality":{"type":"array","items":{"type":"string","maxLength":100}},"appearance":{"type":"string","maxLength":500},"prompt":{"type":"string","maxLength":4000}}}},"scenes":{"type":"array","maxItems":500,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":100},"aliases":{"type":"array","items":{"type":"string","maxLength":100}},"sceneType":{"type":"string","enum":["INTERIOR","EXTERIOR","MIXED","OTHER"],"default":"INTERIOR"},"atmosphere":{"type":"string","maxLength":100},"description":{"type":"string","maxLength":4000},"visualStyle":{"type":"string","maxLength":300},"prompt":{"type":"string","maxLength":4000}}}},"props":{"type":"array","maxItems":500,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":100},"aliases":{"type":"array","items":{"type":"string","maxLength":100}},"propType":{"type":"string","enum":["KEY_PROP","DAILY","WEAPON","DOCUMENT","OTHER"],"default":"KEY_PROP"},"appearance":{"type":"string","maxLength":500},"plotFunction":{"type":"string","maxLength":500},"relatedCharacter":{"type":"string","maxLength":200},"prompt":{"type":"string","maxLength":4000}}}}}}'
where code = 'script-character-scene-recognition' and version_no = 1
  and prompt_template = concat('你是短剧资产识别助手。请仅基于剧本返回严格 JSON。剧本内容：', '$', '{scriptContent}');
