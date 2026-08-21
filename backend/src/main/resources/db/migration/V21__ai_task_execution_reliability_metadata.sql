alter table video_decomposition_episode add column execution_token varchar(64) null;
alter table video_decomposition_episode add column execution_phase varchar(32) null;
alter table video_decomposition_episode add column execution_version int not null default 0;
alter table video_decomposition_episode add column claimed_at datetime null;
alter table video_decomposition_episode add column heartbeat_at datetime null;
alter table video_decomposition_episode add column execution_timeout_at datetime null;
alter table video_decomposition_episode add column retryable boolean not null default false;

alter table video_decomposition_attempt add column idempotency_key varchar(200) null;
alter table video_decomposition_attempt add column retryable boolean not null default false;

create index idx_video_decomposition_episode_claim
  on video_decomposition_episode (status, execution_token, execution_timeout_at);

create index idx_video_decomposition_attempt_idempotency
  on video_decomposition_attempt (episode_id, phase, idempotency_key);

alter table ai_video_task add column execution_token varchar(64) null;
alter table ai_video_task add column execution_phase varchar(32) null;
alter table ai_video_task add column execution_version int not null default 0;
alter table ai_video_task add column claimed_at datetime null;
alter table ai_video_task add column heartbeat_at datetime null;
alter table ai_video_task add column execution_timeout_at datetime null;
alter table ai_video_task add column retryable boolean not null default false;

create index idx_ai_video_task_claim
  on ai_video_task (status, execution_token, execution_timeout_at);

create table ai_video_task_attempt (
  id bigint primary key auto_increment,
  task_id bigint not null,
  attempt_no int not null,
  phase varchar(32) not null,
  status varchar(32) not null,
  idempotency_key varchar(200) null,
  provider_request_id varchar(128) null,
  ai_call_log_id bigint null,
  retryable boolean not null default false,
  error_code varchar(64) null,
  error_message varchar(1000) null,
  started_at datetime not null,
  finished_at datetime null,
  unique key uk_ai_video_task_attempt_no (task_id, attempt_no, phase),
  index idx_ai_video_task_attempt_task (task_id, started_at),
  index idx_ai_video_task_attempt_idempotency (task_id, phase, idempotency_key)
);
