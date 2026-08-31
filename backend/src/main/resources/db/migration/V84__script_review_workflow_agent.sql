alter table review_task add column workflow_agent_code varchar(128) null;
alter table review_task add column workflow_agent_revision bigint null;
alter table review_task add column workflow_agent_run_id bigint null;
alter table review_task add column workflow_phase varchar(32) null;
alter table review_task add column workflow_attempt_no int not null default 0;
alter table review_task add column version_hash varchar(64) null;
alter table review_task add column scope_hash varchar(64) null;
alter table review_task add column dimensions_hash varchar(64) null;
alter table review_task add column fanout_snapshot_id bigint null;
alter table review_task add column aggregation_run_id bigint null;
alter table review_task add column retry_kind varchar(32) null;
alter table review_task add column stale boolean not null default false;

create index idx_review_task_workflow_run on review_task (workflow_agent_run_id);
create index idx_review_task_fanout_snapshot on review_task (fanout_snapshot_id);

create table review_fanout_snapshot (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  task_id bigint not null,
  script_version_id bigint not null,
  attempt_no int not null,
  agent_code varchar(128) not null,
  agent_revision bigint not null,
  skill_revisions_json longtext not null,
  model_id bigint not null,
  review_mode varchar(32) not null,
  selected_dimensions_json longtext not null,
  review_scope_json longtext null,
  version_hash varchar(64) not null,
  scope_hash varchar(64) not null,
  dimensions_hash varchar(64) not null,
  unit_set_hash varchar(64) not null,
  status varchar(32) not null,
  total_units int not null,
  completed_units int not null default 0,
  failed_units int not null default 0,
  current_unit_id bigint null,
  aggregation_run_id bigint null,
  aggregation_status varchar(32) null,
  max_concurrency int not null,
  created_at datetime not null,
  updated_at datetime not null,
  completed_at datetime null,
  canceled_at datetime null,
  unique key uk_review_fanout_snapshot_attempt (task_id, attempt_no),
  index idx_review_fanout_snapshot_status (tenant_id, status, updated_at),
  index idx_review_fanout_snapshot_hashes (task_id, version_hash, scope_hash, dimensions_hash),
  constraint fk_review_fanout_snapshot_task foreign key (task_id) references review_task(id),
  constraint fk_review_fanout_snapshot_version foreign key (script_version_id) references review_script_version(id),
  constraint fk_review_fanout_snapshot_model foreign key (model_id) references ai_model(id),
  constraint fk_review_fanout_snapshot_aggregation_run foreign key (aggregation_run_id)
    references ai_workflow_agent_run(id) on delete set null
);

create table review_fanout_unit (
  id bigint primary key auto_increment,
  snapshot_id bigint not null,
  unit_no int not null,
  unit_key varchar(160) not null,
  scope_json longtext not null,
  start_offset int not null,
  end_offset int not null,
  content_fingerprint varchar(64) not null,
  status varchar(32) not null,
  child_run_id bigint null,
  attempt_no int not null default 0,
  candidate_saved boolean not null default false,
  error_code varchar(128) null,
  error_message varchar(1000) null,
  created_at datetime not null,
  updated_at datetime not null,
  started_at datetime null,
  completed_at datetime null,
  unique key uk_review_fanout_unit_key (snapshot_id, unit_key),
  index idx_review_fanout_unit_status (snapshot_id, status, unit_no),
  constraint fk_review_fanout_unit_snapshot foreign key (snapshot_id)
    references review_fanout_snapshot(id) on delete cascade,
  constraint fk_review_fanout_unit_run foreign key (child_run_id)
    references ai_workflow_agent_run(id) on delete set null
);

create table review_unit_result (
  id bigint primary key auto_increment,
  snapshot_id bigint not null,
  unit_id bigint not null,
  child_run_id bigint not null,
  attempt_no int not null,
  version_hash varchar(64) not null,
  scope_hash varchar(64) not null,
  dimensions_hash varchar(64) not null,
  content_fingerprint varchar(64) not null,
  coverage_json longtext not null,
  candidates_json longtext not null,
  payload_hash varchar(64) not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_review_unit_result_current (snapshot_id, unit_id),
  index idx_review_unit_result_hashes (snapshot_id, version_hash, scope_hash, dimensions_hash),
  constraint fk_review_unit_result_snapshot foreign key (snapshot_id)
    references review_fanout_snapshot(id) on delete cascade,
  constraint fk_review_unit_result_unit foreign key (unit_id)
    references review_fanout_unit(id) on delete cascade,
  constraint fk_review_unit_result_run foreign key (child_run_id)
    references ai_workflow_agent_run(id)
);
