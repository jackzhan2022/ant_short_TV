-- V114 retired the structured review tools but left their history-read grant behind.
-- Remove the obsolete grant from built-in and copied Agents while preserving all other configuration.
delete from ai_workflow_agent_tool
 where tool_code = 'read_review_issue_history';
