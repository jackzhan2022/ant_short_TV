alter table ai_execution_task add column business_call_count int not null default 0;
alter table ai_execution_task add column technical_retry_count int not null default 0;

update ai_workflow_agent
   set system_prompt = '服务端已准备完整可信上下文。严格按 Skill 用 schemaVersion 3 完成整集分镜；为每个分镜提交创意终点 sourceTo，并可为内部镜头提交非递减 sourceAnchor。不要枚举 soundSegmentIds，声音归属和可信原文由后端派生。将表演、情绪和运镜分别写入对应字段，不要混为机械校验条件。只调用 save_episode_storyboards，并以保存成功作为终止动作。',
       revision = revision + 1,
       updated_at = now()
 where code = 'short-drama-storyboard'
   and created_by is null
   and updated_by is null
   and system_prompt = '服务端已准备完整可信上下文。严格按 Skill 用 schemaVersion 2 完成整集分镜；sourceFrom 和 sourceTo 必须放在每个分镜对象内部，根对象禁止出现。soundSegmentIds 只能引用 type 为 DIALOGUE、NARRATION 或 INNER_OS 的来源 ID，禁止引用 ACTION 或 METADATA。只调用 save_episode_storyboards，并以保存成功作为终止动作。';
