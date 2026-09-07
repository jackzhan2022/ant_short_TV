alter table review_fanout_unit add column stage_type varchar(32) not null default 'CONTENT_DISCOVERY';
alter table review_fanout_unit add column dimension varchar(100) null;

create index idx_review_fanout_dimension_stage
  on review_fanout_unit (snapshot_id, stage_type, dimension, status);
