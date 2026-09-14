select version() as mysql_version, database() as database_name;
select 'executions',status,count(*) from ai_execution_task group by status;
select 'image',tenant_id,count(*) from ai_image_task group by tenant_id
union all select 'video',tenant_id,count(*) from ai_video_task group by tenant_id
union all select 'analysis',tenant_id,count(*) from script_analysis_task group by tenant_id
union all select 'review',tenant_id,count(*) from review_task group by tenant_id
union all select 'operation',tenant_id,count(*) from script_ai_operation group by tenant_id
union all select 'storyboard_batch',tenant_id,count(*) from storyboard_batch group by tenant_id
union all select 'decomposition_batch',tenant_id,count(*) from video_decomposition_batch group by tenant_id;
select 'migration',version,success from flyway_schema_history order by installed_rank desc limit 1;
select 'sessions_active',count(*) from auth_session where revoked_at is null and expires_at>now();
