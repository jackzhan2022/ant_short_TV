alter table ai_execution_task add column source_execution_id bigint null;
alter table ai_execution_task add column root_execution_id bigint null;

alter table ai_execution_task add constraint fk_ai_execution_task_source
  foreign key (source_execution_id) references ai_execution_task(id);
alter table ai_execution_task add constraint fk_ai_execution_task_root
  foreign key (root_execution_id) references ai_execution_task(id);

create index idx_ai_execution_task_source on ai_execution_task (source_execution_id);
create unique index uk_ai_execution_task_root_version
  on ai_execution_task (root_execution_id, execution_version);
