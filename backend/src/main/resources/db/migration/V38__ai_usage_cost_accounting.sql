create table ai_usage_line (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  execution_id bigint not null,
  attempt_id bigint null,
  ai_call_log_id bigint null,
  model_id bigint not null,
  metric varchar(32) not null,
  quantity decimal(24,8) not null,
  unit varchar(32) not null,
  source varchar(32) not null,
  dimensions_json text null,
  dimensions_key varchar(500) not null default '',
  observed_at datetime not null,
  adjustment_of_usage_line_id bigint null,
  created_at datetime not null,
  constraint fk_ai_usage_adjustment foreign key (adjustment_of_usage_line_id) references ai_usage_line(id),
  index idx_ai_usage_execution (execution_id, metric, id),
  index idx_ai_usage_attempt (attempt_id, id),
  index idx_ai_usage_call_log (ai_call_log_id, id)
);

create table ai_model_price_version (
  id bigint primary key auto_increment,
  model_id bigint not null,
  version_no int not null,
  status varchar(32) not null,
  effective_from datetime not null,
  effective_to datetime null,
  published_at datetime null,
  created_by bigint null,
  created_at datetime not null,
  constraint uk_ai_model_price_version unique (model_id, version_no),
  index idx_ai_model_price_effective (model_id, status, effective_from, effective_to)
);

create table ai_model_price_component (
  id bigint primary key auto_increment,
  price_version_id bigint not null,
  metric varchar(32) not null,
  unit_size decimal(24,8) not null,
  unit_price decimal(24,12) not null,
  currency varchar(16) not null,
  dimensions_json text null,
  dimensions_key varchar(500) not null default '',
  created_at datetime not null,
  constraint fk_ai_price_component_version foreign key (price_version_id) references ai_model_price_version(id),
  constraint uk_ai_price_component unique (price_version_id, metric, dimensions_key),
  index idx_ai_price_component_match (price_version_id, metric)
);

create table ai_usage_cost_line (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  execution_id bigint not null,
  attempt_id bigint null,
  ai_call_log_id bigint null,
  usage_line_id bigint null,
  price_version_id bigint null,
  price_component_id bigint null,
  model_id bigint not null,
  metric varchar(32) not null,
  quantity decimal(24,8) null,
  unit_size decimal(24,8) null,
  unit_price decimal(24,12) null,
  currency varchar(16) null,
  raw_cost decimal(24,12) null,
  rounded_cost decimal(24,8) null,
  pricing_status varchar(32) not null,
  missing_reason varchar(500) null,
  adjustment_of_cost_line_id bigint null,
  created_at datetime not null,
  constraint fk_ai_usage_cost_usage foreign key (usage_line_id) references ai_usage_line(id),
  constraint fk_ai_usage_cost_adjustment foreign key (adjustment_of_cost_line_id) references ai_usage_cost_line(id),
  constraint uk_ai_usage_cost_usage unique (usage_line_id),
  index idx_ai_usage_cost_execution (execution_id, pricing_status, currency, id),
  index idx_ai_usage_cost_call_log (ai_call_log_id, id)
);
