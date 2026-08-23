alter table project
  add column aspect_ratio varchar(16) null;

alter table project
  add column file_format varchar(32) null;

alter table project
  add column script_type varchar(32) null;

alter table project
  add column breakdown_strength varchar(32) null;

alter table project
  add column cover_source varchar(32) null;

alter table project
  add column visual_style varchar(120) null;

alter table project
  add column initial_script_content text null;
