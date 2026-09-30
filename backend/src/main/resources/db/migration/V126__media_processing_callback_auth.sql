alter table media_processing_job
  add column queue_id varchar(128) null;

alter table media_processing_job
  add column callback_token_hash char(64) null;

alter table media_processing_job
  add column correlation_data varchar(1024) null;

create unique index uk_media_processing_callback_token
  on media_processing_job (callback_token_hash);
