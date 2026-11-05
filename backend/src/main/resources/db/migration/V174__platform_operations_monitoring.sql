-- Operations timestamps are UTC. No business records are modified.
create table ops_event (
 id bigint primary key auto_increment, event_key varchar(160) not null unique,
 occurred_at datetime(3) not null, source varchar(64) not null, code varchar(100),
 location varchar(300), message varchar(1000), stack varchar(2000), fingerprint varchar(128),
 user_id bigint, tenant_id bigint, project_id bigint, execution_id bigint,
 request_id varchar(128), trace_id varchar(128), trusted_context boolean default false,
 duration_ms double, sql_count bigint, sql_ms double, attributes_json text,
 index idx_ops_event_time (occurred_at,id), index idx_ops_event_tenant (tenant_id,occurred_at,id),
 index idx_ops_event_project (project_id,occurred_at,id), index idx_ops_event_fingerprint (fingerprint,occurred_at,id),
 index idx_ops_event_execution (execution_id,occurred_at,id), index idx_ops_event_trace (trace_id,occurred_at,id)
);
create table ops_metric_bucket (
 id bigint primary key auto_increment, bucket_at datetime(3) not null, granularity varchar(8) not null,
 instance_id varchar(128) not null, family varchar(20) not null, dimensions_key varchar(128) not null,
 dimensions_json varchar(2000), tenant_id bigint,project_id bigint,provider_id bigint,model_id bigint,scene varchar(128),route varchar(300),method varchar(12),
 count_value bigint not null default 0, error_count bigint not null default 0,
 business_count bigint not null default 0, sum_ms double not null default 0, max_ms double not null default 0,
 histogram_json varchar(2000), values_json text, updated_at datetime(3) not null,
 unique(bucket_at,granularity,instance_id,family,dimensions_key),
 index idx_ops_metric_range (family,granularity,bucket_at,id),
 index idx_ops_metric_global (family,granularity,dimensions_key,bucket_at,id),
 index idx_ops_metric_provider (family,granularity,provider_id,model_id,bucket_at,id),
 index idx_ops_metric_tenant (family,granularity,tenant_id,project_id,bucket_at,id),
 index idx_ops_metric_route (family,granularity,route,bucket_at,id)
);
create table ops_ai_call_projection (
 call_log_id bigint primary key, occurred_at datetime(3) not null, tenant_id bigint, project_id bigint,
 provider_id bigint, model_id bigint, scene varchar(128), service_type varchar(64), trace_id varchar(128),
 execution_id bigint, attempt_id bigint, transport_outcome varchar(64), business_outcome varchar(64),
 status varchar(32), error_code varchar(100), duration_ms double, prompt_tokens bigint, completion_tokens bigint,
 updated_at datetime(3) not null,
 index idx_ops_ai_time (occurred_at,call_log_id), index idx_ops_ai_provider (provider_id,model_id,occurred_at,call_log_id),
 index idx_ops_ai_tenant (tenant_id,occurred_at,call_log_id), index idx_ops_ai_execution (execution_id,call_log_id)
);
create table ops_execution_observation (
 id bigint primary key auto_increment, execution_id bigint not null, execution_version int not null,
 tenant_id bigint, project_id bigint, model_id bigint, scene varchar(128), task_type varchar(32),
 status varchar(32), phase varchar(128), created_at datetime(3), started_at datetime(3), completed_at datetime(3),
 observed_at datetime(3) not null, next_run_at datetime(3), heartbeat_at datetime(3), claim_expires_at datetime(3),
 point_settlement_status varchar(32), error_code varchar(100), error_message varchar(1000), trace_id varchar(128),
 unique(execution_id,execution_version), index idx_ops_execution_time (observed_at,id),
 index idx_ops_execution_queue (status,next_run_at,observed_at,id),
 index idx_ops_execution_tenant (tenant_id,observed_at,id)
);
create table ops_job_heartbeat (
 id bigint primary key auto_increment, instance_id varchar(128) not null, boot_id varchar(64) not null,
 job_code varchar(250) not null, enabled boolean not null, schedule_type varchar(32), interval_ms bigint,
 schedule_expression varchar(250), expected_at datetime(3), started_at datetime(3), finished_at datetime(3),
 succeeded_at datetime(3), error_at datetime(3), duration_ms double,
 consecutive_failures int not null default 0, error_message varchar(1000), updated_at datetime(3) not null,
 unique(instance_id,boot_id,job_code), index idx_ops_job_updated (updated_at,id),index idx_ops_job_latest (instance_id,job_code,updated_at,id)
);
create table ops_runtime_setting (setting_key varchar(160) primary key,value_json text not null,version bigint not null default 0,updated_at datetime(3) not null);
create table ops_job_lease (job_key varchar(160) primary key,owner_id varchar(160) not null,fencing_token bigint not null,expires_at datetime(3) not null,updated_at datetime(3) not null);
create table ops_alert_rule (
 id bigint primary key auto_increment,name varchar(160) not null,description varchar(1000),enabled boolean not null default true,
 severity varchar(20) not null,metric varchar(100) not null,operator varchar(8) not null,threshold_value double not null,
 recovery_threshold double not null,window_minutes int not null,sustained_minutes int not null,minimum_samples bigint not null,
 cooldown_minutes int not null,dimensions_json varchar(2000) not null default '{}',notify boolean not null default true,
 version bigint not null default 0,created_at datetime(3) not null,updated_at datetime(3) not null
);
create table ops_alert_state (
 state_key varchar(160) primary key,rule_id bigint not null,rule_version bigint not null,status varchar(20) not null,
 breached_since datetime(3),last_window_at datetime(3),healthy_windows int not null default 0,active_event_id bigint,
 last_notified_at datetime(3),version bigint not null default 0,updated_at datetime(3) not null,
 index idx_ops_state_rule (rule_id)
);
create table ops_alert_event (
 id bigint primary key auto_increment,event_key varchar(160) not null unique,rule_id bigint not null,rule_name varchar(160) not null,
 severity varchar(20) not null,status varchar(20) not null,dimensions_json varchar(2000),metric_value double,threshold_value double,
 sample_count bigint not null,fired_at datetime(3) not null,resolved_at datetime(3),acknowledged_at datetime(3),acknowledged_by bigint,
 silence_until datetime(3),silence_reason varchar(1000),updated_at datetime(3) not null,
 index idx_ops_alert_time (fired_at,id), index idx_ops_alert_active (status,updated_at,id)
);
create table ops_alert_action (
 id bigint primary key auto_increment,event_id bigint not null,action varchar(40) not null,actor_id bigint,reason varchar(1000),
 until_at datetime(3),created_at datetime(3) not null,index idx_ops_action_event (event_id,created_at,id)
);
create table ops_notification_outbox (
 id bigint primary key auto_increment,notification_key varchar(200) not null unique,event_id bigint,transition_type varchar(40) not null,
 message text not null,status varchar(32) not null,attempts int not null default 0,next_attempt_at datetime(3),
 lease_owner varchar(160),lease_expires_at datetime(3),delivered_at datetime(3),last_error varchar(1000),
 created_at datetime(3) not null,updated_at datetime(3) not null,
 index idx_ops_outbox_claim (status,next_attempt_at,id),index idx_ops_outbox_event (event_id,id),index idx_ops_outbox_time (created_at,id)
);
insert into platform_permission(code,name,resource,action,created_at,updated_at) values
 ('PLATFORM_OPERATIONS_VIEW','查看平台运维指标','PLATFORM_OPERATIONS','VIEW',current_timestamp,current_timestamp),
 ('PLATFORM_OPERATIONS_LOG_VIEW','查看平台运维日志','PLATFORM_OPERATIONS','LOG_VIEW',current_timestamp,current_timestamp),
 ('PLATFORM_OPERATIONS_RULE_EDIT','编辑平台告警规则','PLATFORM_OPERATIONS','RULE_EDIT',current_timestamp,current_timestamp),
 ('PLATFORM_OPERATIONS_ALERT_MANAGE','处理平台告警','PLATFORM_OPERATIONS','ALERT_MANAGE',current_timestamp,current_timestamp),
 ('PLATFORM_OPERATIONS_SETTINGS_EDIT','编辑平台监控设置','PLATFORM_OPERATIONS','SETTINGS_EDIT',current_timestamp,current_timestamp);
insert into platform_role_permission(role_id,permission_id,created_at)
select role.id,permission.id,current_timestamp from platform_role role join platform_permission permission
 on permission.code in ('PLATFORM_OPERATIONS_VIEW','PLATFORM_OPERATIONS_LOG_VIEW','PLATFORM_OPERATIONS_RULE_EDIT','PLATFORM_OPERATIONS_ALERT_MANAGE','PLATFORM_OPERATIONS_SETTINGS_EDIT') where role.code='PLATFORM_ADMIN';
