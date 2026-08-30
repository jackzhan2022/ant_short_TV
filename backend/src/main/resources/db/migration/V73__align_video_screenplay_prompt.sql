insert into ai_skill_definition
  (code, version_no, name, category, content, status, published, created_by, created_at, updated_at)
select current_skill.code,
       (select max(all_skill.version_no) + 1 from ai_skill_definition all_skill where all_skill.code = current_skill.code),
       current_skill.name, current_skill.category,
       '请只返回合法 JSON，不要解释，不要把 JSON 外层放进 Markdown 代码块。若任务明确要求某个 JSON 字符串字段包含 Markdown，该字段必须保留指定 Markdown。',
       'ENABLED', false, current_skill.created_by, current_timestamp, current_timestamp
from ai_skill_definition current_skill
where current_skill.code = 'strict-json-output'
  and current_skill.published = true
  and current_skill.status = 'ENABLED';

insert into ai_agent_definition
  (code, version_no, name, description, prompt_template, output_schema, status, published, created_by, created_at, updated_at)
select current_agent.code,
       (select max(all_agent.version_no) + 1 from ai_agent_definition all_agent where all_agent.code = current_agent.code),
       current_agent.name, current_agent.description,
       concat(
         '你是资深的影视编剧与多模态视频分析专家。逐帧解析输入的第 ', '$', '{episodeNo}',
         ' 集视频，按视频发生顺序客观还原，不改变事件顺序，不补充视频无法确认的事实。', char(10), char(10),
         'script 字段必须使用固定 Markdown 拍摄剧本格式：', char(10),
         '# 第', '$', '{episodeNo}', '集：标题', char(10), char(10),
         '## ', '$', '{episodeNo}', '-1 夜 内 地点', char(10), char(10),
         '出场人物：人物A、人物B', char(10), char(10),
         '动作和环境使用独立段落。人物对白依次写角色名、可选的全角括号情绪或动作、无引号台词正文；不要另写字幕行。',
         '画外音和内心独白使用（OS）和（VO）。场次从 1 连续编号，每场都有出场人物和非空正文。',
         '最后一行必须是“——本集完”。悬念通过最后的剧情动作自然呈现，不得另写结尾钩子标签，不得编造悬念。', char(10), char(10),
         '只输出完整合法的 JSON 对象 {"script":"完整剧本文本"}。JSON 外层不要代码块或解释；script 是非空 Markdown 字符串，JSON 不得截断。'
       ),
       current_agent.output_schema, 'ENABLED', false, current_agent.created_by, current_timestamp, current_timestamp
from ai_agent_definition current_agent
where current_agent.code = 'video-understanding'
  and current_agent.published = true
  and current_agent.status = 'ENABLED';

insert into ai_agent_skill (agent_definition_id, skill_definition_id, sort_order)
select new_agent.id,
       case when bound_skill.code = 'strict-json-output' then new_strict_skill.id else bound_skill.id end,
       binding.sort_order
from ai_agent_definition old_agent
join ai_agent_definition new_agent
  on new_agent.code = old_agent.code
 and new_agent.version_no = (select max(candidate.version_no) from ai_agent_definition candidate where candidate.code = old_agent.code)
join ai_agent_skill binding on binding.agent_definition_id = old_agent.id
join ai_skill_definition bound_skill on bound_skill.id = binding.skill_definition_id
join ai_skill_definition new_strict_skill
  on new_strict_skill.code = 'strict-json-output'
 and new_strict_skill.version_no = (select max(candidate.version_no) from ai_skill_definition candidate where candidate.code = 'strict-json-output')
where old_agent.code = 'video-understanding'
  and old_agent.published = true;

update ai_agent_definition
set published = false, updated_at = current_timestamp
where code = 'video-understanding' and published = true;

update ai_agent_definition
set published = true, updated_at = current_timestamp
where code = 'video-understanding'
  and version_no = (select latest.version_no from (
    select max(version_no) version_no from ai_agent_definition where code = 'video-understanding'
  ) latest);

update ai_skill_definition
set published = true, updated_at = current_timestamp
where code = 'strict-json-output'
  and version_no = (select latest.version_no from (
    select max(version_no) version_no from ai_skill_definition where code = 'strict-json-output'
  ) latest);
