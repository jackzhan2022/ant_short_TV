alter table ai_image_result
  add column mime_type varchar(100) null;

alter table ai_image_result
  add column thumbnail_path varchar(1000) null;
