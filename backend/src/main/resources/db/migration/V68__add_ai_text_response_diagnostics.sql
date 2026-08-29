alter table ai_call_log add column response_length int null;
alter table ai_call_log add column finish_reason varchar(64) null;
alter table ai_call_log add column truncated boolean not null default false;
