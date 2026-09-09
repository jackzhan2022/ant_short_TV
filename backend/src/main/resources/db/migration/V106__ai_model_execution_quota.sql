create table ai_model_execution_quota (
  model_id bigint primary key,
  concurrency_limit int not null,
  updated_at datetime not null
);
