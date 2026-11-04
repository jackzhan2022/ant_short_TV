alter table project add column visual_style_version varchar(8) not null default 'V1';
alter table project add column visual_style_code varchar(80) not null default '';
alter table script_analysis_task add column art_style_snapshot longtext null;
alter table script_ai_operation add column art_style_snapshot longtext null;
