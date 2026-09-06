-- Existing script-review Agents can predate the semantic-quality rollout.
-- Grant only the new phase-specific Skill and tools; preserve all other Agent configuration.
insert ignore into ai_workflow_agent_skill (agent_id, skill_code, load_order, created_at)
select id, 'script-review-semantic-quality', 99, now()
  from ai_workflow_agent
 where code = 'script-review';

insert ignore into ai_workflow_agent_tool (agent_id, tool_code, created_at)
select id, 'read_review_candidates', now()
  from ai_workflow_agent
 where code = 'script-review';

insert ignore into ai_workflow_agent_tool (agent_id, tool_code, created_at)
select id, 'save_review_semantic_decisions', now()
  from ai_workflow_agent
 where code = 'script-review';
