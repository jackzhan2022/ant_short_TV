alter table inspiration_creation add column thumbnail_path varchar(1000) null;
alter table inspiration_creation add column thumbnail_url varchar(1000) null;
alter table inspiration_creation add column thumbnail_mime_type varchar(100) null;
alter table inspiration_creation add column thumbnail_file_size bigint null;
alter table inspiration_creation add column thumbnail_status varchar(32) null;
alter table inspiration_creation add column thumbnail_error text null;

create index idx_inspiration_creation_thumbnail_backfill
  on inspiration_creation (import_status, thumbnail_status, id);
