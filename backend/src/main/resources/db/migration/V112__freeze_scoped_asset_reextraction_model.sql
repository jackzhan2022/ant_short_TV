alter table scoped_asset_reextraction_snapshot
  add column model_id bigint null after prompt_policy;

create index idx_scoped_asset_reextraction_model
  on scoped_asset_reextraction_snapshot (model_id);
