create table ai_execution_task (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  user_id bigint not null,
  project_id bigint null,
  scene varchar(64) not null,
  capability varchar(64) not null,
  business_type varchar(64) not null,
  business_id bigint null,
  requested_model_id bigint null,
  resolved_model_id bigint null,
  redacted_input_json text null,
  status varchar(32) not null,
  phase varchar(64) not null,
  progress int not null default 0,
  execution_version int not null default 1,
  client_idempotency_key varchar(200) not null,
  trace_id varchar(128) not null,
  priority int not null default 100,
  next_run_at datetime null,
  claim_token varchar(64) null,
  claimed_at datetime null,
  heartbeat_at datetime null,
  claim_expires_at datetime null,
  retryable boolean not null default false,
  result_type varchar(64) null,
  result_id bigint null,
  error_code varchar(64) null,
  error_message varchar(1000) null,
  usage_cost_status varchar(32) not null default 'PENDING',
  provider_cost_summary_json text null,
  point_settlement_status varchar(32) not null default 'PENDING',
  reserved_points decimal(24,8) not null default 0,
  settled_points decimal(24,8) not null default 0,
  released_points decimal(24,8) not null default 0,
  started_at datetime null,
  completed_at datetime null,
  canceled_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  constraint uk_ai_execution_task_idempotency
    unique (tenant_id, scene, client_idempotency_key),
  index idx_ai_execution_task_eligibility (status, next_run_at, priority, created_at),
  index idx_ai_execution_task_tenant_running (tenant_id, status, claim_expires_at),
  index idx_ai_execution_task_business (tenant_id, business_type, business_id, execution_version),
  index idx_ai_execution_task_trace (trace_id)
);

create table ai_execution_attempt (
  id bigint primary key auto_increment,
  execution_id bigint not null,
  execution_version int not null,
  phase varchar(64) not null,
  attempt_no int not null,
  status varchar(32) not null,
  idempotency_key varchar(200) not null,
  provider_contacted boolean not null default false,
  provider_id bigint null,
  model_id bigint null,
  provider_request_id varchar(128) null,
  external_task_id varchar(256) null,
  ai_call_log_id bigint null,
  transport_outcome varchar(32) null,
  business_outcome varchar(32) null,
  retryable boolean not null default false,
  retry_count int not null default 0,
  next_retry_at datetime null,
  error_code varchar(64) null,
  error_message varchar(1000) null,
  started_at datetime not null,
  provider_contacted_at datetime null,
  finished_at datetime null,
  constraint fk_ai_execution_attempt_execution
    foreign key (execution_id) references ai_execution_task(id),
  constraint uk_ai_execution_attempt_idempotency
    unique (execution_id, phase, execution_version, idempotency_key),
  constraint uk_ai_execution_attempt_no
    unique (execution_id, phase, execution_version, attempt_no),
  index idx_ai_execution_attempt_execution (execution_id, started_at),
  index idx_ai_execution_attempt_provider_task (provider_id, external_task_id)
);

alter table ai_call_log add column execution_id bigint null;
alter table ai_call_log add column attempt_id bigint null;
alter table ai_call_log add column execution_version int null;
alter table ai_call_log add column phase varchar(64) null;
create index idx_ai_call_log_execution on ai_call_log (execution_id, attempt_id);

alter table ai_image_task add column execution_id bigint null;
alter table ai_image_result add column execution_id bigint null;
alter table ai_video_task add column execution_id bigint null;
alter table ai_video_result add column execution_id bigint null;
alter table script_analysis_task add column execution_id bigint null;
alter table script_analysis_result add column execution_id bigint null;
alter table review_task add column execution_id bigint null;

create index idx_ai_image_task_execution on ai_image_task (execution_id);
create index idx_ai_image_result_execution on ai_image_result (execution_id);
create index idx_ai_video_task_execution on ai_video_task (execution_id);
create index idx_ai_video_result_execution on ai_video_result (execution_id);
create index idx_script_analysis_task_execution on script_analysis_task (execution_id);
create index idx_script_analysis_result_execution on script_analysis_result (execution_id);
create index idx_review_task_execution on review_task (execution_id);
