create table character_variant_similarity_resolution (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  project_id bigint not null,
  check_id bigint not null,
  character_id bigint not null,
  left_variant_id bigint not null,
  right_variant_id bigint not null,
  pair_fingerprint varchar(64) not null,
  decision varchar(16) not null,
  kept_variant_id bigint null,
  created_by bigint not null,
  created_at datetime not null,
  unique key uk_character_variant_resolution (tenant_id, project_id, left_variant_id, right_variant_id, pair_fingerprint),
  index idx_character_variant_resolution_report (tenant_id, project_id, check_id),
  index idx_character_variant_resolution_decision (tenant_id, project_id, decision, id)
);
