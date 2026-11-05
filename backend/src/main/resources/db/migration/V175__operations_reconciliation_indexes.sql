-- Narrow, lightweight source scans. These indexes do not change business state or retention.
create index idx_ai_execution_ops_updated on ai_execution_task(updated_at,id);
create index idx_ai_execution_ops_status on ai_execution_task(status,id);
create index idx_ai_execution_ops_settlement on ai_execution_task(point_settlement_status,status,id);
create index idx_ai_call_ops_created on ai_call_log(created_at,id);
