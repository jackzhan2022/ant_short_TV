create table review_pipeline_stage (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  task_id bigint not null,
  snapshot_id bigint not null,
  stage_key varchar(160) not null,
  stage_type varchar(32) not null,
  dimension varchar(100) null,
  status varchar(32) not null,
  run_id bigint null,
  attempt_no int not null default 0,
  version_hash varchar(64) not null,
  scope_hash varchar(64) not null,
  dimensions_hash varchar(64) not null,
  input_hash varchar(64) not null,
  coverage_json longtext null,
  candidate_count int null,
  decision_count int null,
  error_code varchar(128) null,
  error_message varchar(1000) null,
  created_at datetime not null,
  updated_at datetime not null,
  started_at datetime null,
  completed_at datetime null,
  unique key uk_review_pipeline_stage (snapshot_id, stage_key),
  index idx_review_pipeline_stage_status (snapshot_id, stage_type, status),
  constraint fk_review_pipeline_stage_snapshot foreign key (snapshot_id)
    references review_fanout_snapshot(id) on delete cascade,
  constraint fk_review_pipeline_stage_run foreign key (run_id)
    references ai_workflow_agent_run(id) on delete set null
);

create table review_candidate_audit (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  task_id bigint not null,
  snapshot_id bigint not null,
  unit_id bigint not null,
  discovery_run_id bigint not null,
  candidate_key varchar(64) not null,
  dimension varchar(100) not null,
  candidate_no int not null,
  status varchar(32) not null,
  raw_payload_json longtext not null,
  validation_errors_json longtext null,
  source_fingerprint varchar(64) not null,
  created_at datetime not null,
  unique key uk_review_candidate_audit (snapshot_id, unit_id, candidate_key),
  index idx_review_candidate_status (snapshot_id, dimension, status),
  constraint fk_review_candidate_snapshot foreign key (snapshot_id)
    references review_fanout_snapshot(id) on delete cascade,
  constraint fk_review_candidate_unit foreign key (unit_id)
    references review_fanout_unit(id) on delete cascade,
  constraint fk_review_candidate_run foreign key (discovery_run_id)
    references ai_workflow_agent_run(id)
);

create table review_semantic_decision (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  task_id bigint not null,
  snapshot_id bigint not null,
  candidate_id bigint not null,
  quality_run_id bigint not null,
  decision varchar(32) not null,
  confidence decimal(5,4) not null,
  rationale varchar(2000) not null,
  severity_decision varchar(32) null,
  evidence_refs_json longtext not null,
  duplicate_cluster_key varchar(160) null,
  created_at datetime not null,
  unique key uk_review_semantic_candidate (candidate_id),
  index idx_review_semantic_status (snapshot_id, decision),
  constraint fk_review_semantic_candidate foreign key (candidate_id)
    references review_candidate_audit(id) on delete cascade,
  constraint fk_review_semantic_snapshot foreign key (snapshot_id)
    references review_fanout_snapshot(id) on delete cascade,
  constraint fk_review_semantic_run foreign key (quality_run_id)
    references ai_workflow_agent_run(id)
);
