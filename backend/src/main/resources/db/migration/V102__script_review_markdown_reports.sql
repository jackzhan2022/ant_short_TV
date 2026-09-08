alter table review_task add column result_format varchar(32) null;
alter table review_task add column report_markdown longtext null;

alter table review_unit_result add column report_markdown longtext null;
