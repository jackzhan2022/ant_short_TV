alter table ai_call_log add column cached_input_tokens int null;
alter table ai_call_log add column cache_write_tokens int null;
alter table ai_call_log add column prompt_cache_key varchar(255) null;

