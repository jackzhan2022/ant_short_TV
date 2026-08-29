-- Repair the initial overlay prompts without overwriting administrator revisions.
update ai_agent_definition
set prompt_template = concat('你是中文短剧结构分析助手。请基于剧本输出严格 JSON。剧本内容：', '$', '{scriptContent}')
where code = 'script-global-understanding' and version_no = 1
  and prompt_template = concat('你是中文短剧结构分析助手。请基于剧本输出严格 JSON。剧本内容：', '$', '{scriptContent}');

update ai_agent_definition
set prompt_template = concat('你是短剧分集助手。请根据剧情理解和原剧本智能拆分剧集，只返回严格 JSON。剧情理解：', '$', '{globalUnderstanding}', ' 原剧本：', '$', '{scriptContent}')
where code = 'script-episode-split' and version_no = 1
  and prompt_template = '你是短剧分集助手。请根据剧情理解和原剧本智能拆分剧集，只返回严格 JSON。';

update ai_agent_definition
set prompt_template = concat('你是短剧概要提炼助手。请为输入的每一集返回严格 JSON。分集内容：', '$', '{episodes}')
where code = 'script-episode-summary' and version_no = 1
  and prompt_template = '你是短剧概要提炼助手。请为输入的每一集返回严格 JSON。';

update ai_agent_definition
set prompt_template = concat('你是短剧资产识别助手。请仅基于剧本返回严格 JSON。剧本内容：', '$', '{scriptContent}')
where code = 'script-character-scene-recognition' and version_no = 1
  and prompt_template = '你是短剧资产识别助手。请仅基于剧本返回严格 JSON。';
