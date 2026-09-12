SHOW GRANTS;
SELECT ID,COMMAND,TIME,STATE FROM information_schema.processlist;
SELECT t.id,t.tenant_id,t.status,t.created_at,t.updated_at,t.execution_id,e.status FROM script_analysis_task t LEFT JOIN ai_execution_task e ON e.id=t.execution_id WHERE t.status IN ('PENDING','RUNNING');
SELECT 'analysis',status,count(*) FROM script_analysis_task GROUP BY status;
SELECT 'review',status,count(*) FROM review_task GROUP BY status;
SELECT 'decomposition',status,count(*) FROM video_decomposition_episode GROUP BY status;
SELECT 'operation',status,count(*) FROM script_ai_operation GROUP BY status;
