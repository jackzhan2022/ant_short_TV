-- Repair only the exact incomplete V1 seed values. Administrator-authored
-- revisions are intentionally left untouched.
update ai_agent_definition
set prompt_template = concat(
  '你是专业的中文短剧剧本改写助手。请基于原剧本和改写要求完成改写，保留核心人物关系与主要情节。原剧本：',
  '$', '{scriptContent}', ' 改写要求：', '$', '{rewriteRequirement}')
where code = 'script-rewrite' and version_no = 1
  and prompt_template = '你是专业的中文短剧剧本改写助手。';

update ai_agent_definition
set prompt_template = concat(
  '请仅基于剧本内容提取角色信息并返回严格 JSON。剧本标题：', '$', '{scriptTitle}',
  ' 剧本内容：', '$', '{scriptContent}')
where code = 'script-character-extract' and version_no = 1
  and prompt_template = '请仅基于剧本内容提取角色信息并返回严格 JSON。';

update ai_agent_definition
set prompt_template = concat(
  '请仅基于剧本内容提取场景信息并返回严格 JSON。剧本标题：', '$', '{scriptTitle}',
  ' 剧本内容：', '$', '{scriptContent}')
where code = 'script-scene-extract' and version_no = 1
  and prompt_template = '请仅基于剧本内容提取场景信息并返回严格 JSON。';

update ai_agent_definition
set prompt_template = concat(
  '请仅基于剧本内容提取关键道具信息并返回严格 JSON。剧本标题：', '$', '{scriptTitle}',
  ' 剧本内容：', '$', '{scriptContent}')
where code = 'script-prop-extract' and version_no = 1
  and prompt_template = '请仅基于剧本内容提取道具信息并返回严格 JSON。';

update ai_agent_definition
set prompt_template = concat(
  '你是影视编剧与多模态视频分析专家。请逐帧解析第 ', '$', '{episodeNo}',
  ' 集视频，只输出合法 JSON：{"script":"完整剧本文本"}。')
where code = 'video-understanding' and version_no = 1
  and prompt_template = '你是影视编剧与多模态视频分析专家。只输出合法 JSON。';

update ai_agent_definition
set prompt_template = concat(
  '请把以下第 ', '$', '{episodeNo}', ' 集视频拆解 JSON 改写成中文短剧剧本。JSON：',
  '$', '{normalizedJson}')
where code = 'video-script-draft' and version_no = 1
  and prompt_template = '请把视频拆解 JSON 改写成中文短剧剧本。';

update ai_agent_definition
set prompt_template = concat(
  '你是专业的中文短剧剧本审核 Agent。本轮审核模式：', '$', '{reviewMode}',
  ' 审核维度：', '$', '{selectedDimensions}', ' 审核范围：', '$', '{reviewScope}',
  ' 上一轮问题：', '$', '{previousIssues}', ' 全局索引：', '$', '{globalIndex}',
  ' 剧本标题：', '$', '{scriptTitle}', ' 剧本内容：', '$', '{scriptContent}',
  '。只返回严格 JSON。')
where code = 'script-review' and version_no = 1
  and prompt_template = '你是专业的中文短剧剧本审核 Agent。只返回严格 JSON。';

-- Complete the initial Agent-to-Skill associations. Runtime resolution filters
-- to the published Skill revision, so stale historical bindings remain inert.
insert into ai_agent_skill (agent_definition_id, skill_definition_id)
select a.id, s.id
from ai_agent_definition a
join ai_skill_definition s on s.published = true and s.status = 'ENABLED'
where a.version_no = 1
  and (
    (a.code = 'script-rewrite' and s.code in ('no-invention', 'short-drama-structure'))
    or (a.code in ('script-character-extract', 'script-scene-extract', 'script-prop-extract')
        and s.code in ('strict-json-output', 'no-invention', 'stable-entity-naming'))
    or (a.code = 'script-character-scene-recognition' and s.code = 'stable-entity-naming')
    or (a.code = 'script-review'
        and s.code in ('strict-json-output', 'no-invention', 'script-review-rules', 'review-json-output'))
  )
  and not exists (
    select 1 from ai_agent_skill x
    where x.agent_definition_id = a.id and x.skill_definition_id = s.id
  );
