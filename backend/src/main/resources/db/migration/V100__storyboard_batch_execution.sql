create table storyboard_batch (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  script_id bigint not null,
  name varchar(200) not null,
  idempotency_key varchar(200) not null,
  created_by bigint not null,
  created_at datetime not null,
  unique key uk_storyboard_batch_idempotency (tenant_id, project_id, idempotency_key),
  index idx_storyboard_batch_project_created (tenant_id, project_id, created_at)
);

create table storyboard_batch_item (
  id bigint primary key auto_increment,
  batch_id bigint not null,
  tenant_id bigint not null,
  project_id bigint not null,
  episode_id bigint not null,
  episode_no int not null,
  execution_id bigint not null,
  created_at datetime not null,
  unique key uk_storyboard_batch_episode (batch_id, episode_id),
  unique key uk_storyboard_batch_execution (execution_id),
  index idx_storyboard_batch_item_batch (tenant_id, project_id, batch_id, episode_no),
  constraint fk_storyboard_batch_item_batch foreign key (batch_id) references storyboard_batch(id),
  constraint fk_storyboard_batch_item_execution foreign key (execution_id) references ai_execution_task(id)
);
