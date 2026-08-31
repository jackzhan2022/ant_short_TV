create table script_split_snapshot (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  parent_run_id bigint not null,
  content_hash varchar(128) not null,
  mode varchar(32) not null,
  fallback_reason varchar(64) not null,
  status varchar(32) not null,
  planner_version varchar(64) not null,
  total_chunks int not null default 0,
  completed_chunks int not null default 0,
  failed_chunks int not null default 0,
  created_at datetime not null,
  finished_at datetime null,
  updated_at datetime not null,
  index idx_script_split_snapshot_lookup
    (tenant_id, script_id, content_hash, status),
  index idx_script_split_snapshot_run (parent_run_id, created_at),
  constraint fk_script_split_snapshot_script foreign key (script_id) references script(id),
  constraint fk_script_split_snapshot_run foreign key (parent_run_id)
    references ai_workflow_agent_run(id)
);

create table script_split_chunk (
  id bigint primary key auto_increment,
  snapshot_id bigint not null,
  chunk_no int not null,
  core_start int not null,
  core_end int not null,
  context_start int not null,
  context_end int not null,
  content_hash varchar(128) not null,
  status varchar(32) not null,
  ai_call_log_id bigint null,
  candidate_json json null,
  error_code varchar(128) null,
  error_message varchar(2000) null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_script_split_chunk_no (snapshot_id, chunk_no),
  index idx_script_split_chunk_retry (snapshot_id, status, chunk_no),
  constraint fk_script_split_chunk_snapshot foreign key (snapshot_id)
    references script_split_snapshot(id),
  constraint fk_script_split_chunk_call foreign key (ai_call_log_id)
    references ai_call_log(id) on delete set null
);

update ai_workflow_agent
   set max_tokens = 16384,
       revision = revision + 1,
       updated_at = now()
 where code = 'short-drama-episode-splitting'
   and max_tokens = 32768;
