insert into ai_workflow_agent_tool(agent_id,tool_code,created_at)
select id,'search_script_assets',now() from ai_workflow_agent a
where code='short-drama-asset-recognition' and not exists
 (select 1 from ai_workflow_agent_tool t where t.agent_id=a.id and t.tool_code='search_script_assets');
insert into ai_workflow_agent_tool(agent_id,tool_code,created_at)
select id,'read_asset_details',now() from ai_workflow_agent a
where code='short-drama-asset-recognition' and not exists
 (select 1 from ai_workflow_agent_tool t where t.agent_id=a.id and t.tool_code='read_asset_details');
update ai_workflow_agent set revision=revision+1, max_steps=greatest(max_steps,12),
 system_prompt=concat(system_prompt,
 ' 资产目录只含候选摘要，遗漏不代表不存在。新建前调用 search_script_assets 按规范名及明确别名检索，复用匹配 key；需要形态时调用 read_asset_details 并按 nextCursor 续页。先 read_current_episode，最后 save_episode_assets。'),
 updated_at=now() where code='short-drama-asset-recognition';
