insert into ai_workflow_agent_tool (agent_id, tool_code, created_at)
select agent.id, 'read_script_structure', now()
  from ai_workflow_agent agent
 where agent.code = 'short-drama-episode-splitting'
   and not exists (
       select 1
         from ai_workflow_agent_tool existing
        where existing.agent_id = agent.id
          and existing.tool_code = 'read_script_structure'
   );

insert into ai_workflow_agent_tool (agent_id, tool_code, created_at)
select agent.id, 'analyze_script_chunks', now()
  from ai_workflow_agent agent
 where agent.code = 'short-drama-episode-splitting'
   and not exists (
       select 1
         from ai_workflow_agent_tool existing
        where existing.agent_id = agent.id
          and existing.tool_code = 'analyze_script_chunks'
   );
