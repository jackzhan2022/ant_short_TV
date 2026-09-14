select distinct cl.execution_id, bi.batch_id from storyboard s
join ai_workflow_agent_run_step step on step.run_id=s.generated_by_run_id
join ai_call_log cl on cl.id=step.ai_call_log_id and cl.tenant_id=s.tenant_id
left join storyboard_batch_item bi on bi.execution_id=cl.execution_id and bi.tenant_id=s.tenant_id
where s.tenant_id=1 and s.deleted_at is null
and (cl.execution_id in (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20) or bi.batch_id in (1,2))
and regexp_like(s.shot_plan_json,'"(warnings|classificationWarnings)"\\s*:\\s*\\[\\s*[^\\s\\]]');
