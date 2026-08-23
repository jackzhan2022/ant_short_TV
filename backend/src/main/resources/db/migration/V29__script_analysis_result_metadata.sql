alter table script_analysis_result
  add column duration_ms bigint null;

alter table script_analysis_result
  add column error_code varchar(64) null;

alter table script_analysis_result
  add column error_message varchar(1000) null;

alter table script_analysis_result
  add column retryable boolean not null default false;
