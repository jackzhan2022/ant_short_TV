-- Seed the code-level registry into the editable overlay.  The NOT EXISTS
-- guards keep this migration compatible with installations that pre-created
-- custom revisions before upgrading.
insert into ai_skill_definition (code, version_no, name, category, content, status, published, created_at, updated_at)
select s.code, 1, s.name, s.category, s.content, 'ENABLED', true, current_timestamp, current_timestamp
from (
  select 'strict-json-output' code, '严格 JSON 输出' name, 'OUTPUT' category, '请只返回合法 JSON，不要解释，不要 Markdown，不要代码块。' content
  union all select 'no-invention', '不得编造', 'SAFETY', '缺失信息使用空值，不补充无法从输入确认的事实。'
  union all select 'stable-entity-naming', '实体命名稳定', 'DOMAIN', '角色、场景和道具名称保持稳定，便于后续合并。'
  union all select 'short-drama-structure', '短剧结构化表达', 'DOMAIN', '按短剧制作所需的角色、场景、道具、动作和对白维度组织内容。'
  union all select 'script-review-rules', '剧本审核规则', 'REVIEW', '从完整性、一致性、逻辑性和可执行性检查剧本。'
  union all select 'review-json-output', '审核结果 JSON', 'OUTPUT', '审核结果必须按约定 JSON 结构返回。'
) s
where not exists (select 1 from ai_skill_definition e where e.code = s.code and e.version_no = 1);

insert into ai_agent_definition (code, version_no, name, description, prompt_template, output_schema, status, published, created_at, updated_at)
select a.code, 1, a.name, a.description, a.prompt_template, a.output_schema, 'ENABLED', true, current_timestamp, current_timestamp
from (
  select 'script-global-understanding' code, '剧情全局理解' name, '理解剧本主线、人物关系、核心冲突和整体节奏。' description, concat('你是中文短剧结构分析助手。请基于剧本输出严格 JSON。剧本内容：', '$', '{scriptContent}') prompt_template, '{"logline":"","themes":[],"characters":[],"relationships":[],"coreConflict":"","turningPoints":[],"endingHook":""}' output_schema
  union all select 'script-episode-split', '剧集智能拆分', '根据剧情节点、冲突和悬念把正文拆成剧集。', '你是短剧分集助手。请根据剧情理解和原剧本智能拆分剧集，只返回严格 JSON。', '{"episodes":[]}'
  union all select 'script-episode-summary', '剧集概要提炼', '为每一集提炼概要、看点和结尾悬念。', '你是短剧概要提炼助手。请为输入的每一集返回严格 JSON。', '{"episodes":[]}'
  union all select 'script-character-scene-recognition', '角色场景识别', '从剧本中识别角色、场景和关键道具。', '你是短剧资产识别助手。请仅基于剧本返回严格 JSON。', '{"characters":[],"scenes":[],"props":[]}'
  union all select 'script-rewrite', 'AI 改写剧本', '根据改写要求重写短剧剧本。', '你是专业的中文短剧剧本改写助手。', '{}'
  union all select 'script-character-extract', '提取角色', '从剧本中提取角色结构化信息。', '请仅基于剧本内容提取角色信息并返回严格 JSON。', '{"characters":[]}'
  union all select 'script-scene-extract', '提取场景', '从剧本中提取场景结构化信息。', '请仅基于剧本内容提取场景信息并返回严格 JSON。', '{"scenes":[]}'
  union all select 'script-prop-extract', '提取道具', '从剧本中提取关键道具结构化信息。', '请仅基于剧本内容提取道具信息并返回严格 JSON。', '{"props":[]}'
  union all select 'video-understanding', '剧本拆剧：视频理解', '分析短剧视频并输出结构化拆剧信息。', '你是影视编剧与多模态视频分析专家。只输出合法 JSON。', '{"script":""}'
  union all select 'video-script-draft', '剧本拆剧：草稿生成', '把视频拆解 JSON 改写成可审核的中文短剧剧本。', '请把视频拆解 JSON 改写成中文短剧剧本。', '{}'
  union all select 'script-review', '剧本审核', '审核短剧剧本内容并输出问题、评分和结论。', '你是专业的中文短剧剧本审核 Agent。只返回严格 JSON。', '{"overallScore":0,"overallConclusion":"PASS","summary":"","issues":[]}'
) a
where not exists (select 1 from ai_agent_definition e where e.code = a.code and e.version_no = 1);

insert into ai_agent_skill (agent_definition_id, skill_definition_id)
select a.id, s.id from ai_agent_definition a join ai_skill_definition s
  on s.code in ('strict-json-output', 'no-invention', 'short-drama-structure')
where a.version_no = 1 and a.published = true
  and a.code in ('script-global-understanding','script-episode-split','script-episode-summary','script-character-scene-recognition','video-understanding','video-script-draft')
  and not exists (select 1 from ai_agent_skill x where x.agent_definition_id=a.id and x.skill_definition_id=s.id);
