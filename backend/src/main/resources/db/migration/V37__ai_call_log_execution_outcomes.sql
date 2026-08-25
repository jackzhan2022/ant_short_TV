alter table ai_call_log add column idempotency_key varchar(200) null;
alter table ai_call_log add column external_task_id varchar(256) null;
alter table ai_call_log add column transport_outcome varchar(32) null;
alter table ai_call_log add column business_outcome varchar(32) null;
create index idx_ai_call_log_external_task on ai_call_log (provider_id, external_task_id);
create index idx_ai_call_log_idempotency on ai_call_log (execution_id, phase, idempotency_key);
