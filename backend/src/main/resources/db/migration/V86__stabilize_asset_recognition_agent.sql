create index idx_script_analysis_fanout_stage
  on script_analysis_fanout_snapshot (stage_id);

alter table script_analysis_fanout_snapshot
  drop index uk_script_analysis_fanout_attempt;

alter table script_analysis_fanout_snapshot
  add constraint uk_script_analysis_fanout_attempt
  unique (stage_id, attempt_no, agent_code, agent_revision, model_id);

update ai_workflow_agent
   set system_prompt = '严格按已加载 Skill 执行：先读取当前剧集，再分析实体与逐字证据，最后调用保存工具；五个数组必须始终存在，保存成功前不得声称完成。',
       max_tokens = 4096,
       max_steps = 6,
       revision = revision + 1,
       updated_at = now()
 where code = 'short-drama-asset-recognition'
   and (max_tokens <> 4096
     or max_steps <> 6
     or system_prompt <> '严格按已加载 Skill 执行：先读取当前剧集，再分析实体与逐字证据，最后调用保存工具；五个数组必须始终存在，保存成功前不得声称完成。');
