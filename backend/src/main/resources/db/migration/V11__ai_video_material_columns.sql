alter table material add column source_result_id bigint null;
alter table material add column cover_url varchar(1000) null;
alter table material add column duration_seconds decimal(8,2) null;
alter table material add column format varchar(32) null;
alter table material add column status varchar(32) null;
alter table material add column updated_at datetime null;
alter table material add column deleted_at datetime null;

create index idx_material_source_result on material (source_result_id);
