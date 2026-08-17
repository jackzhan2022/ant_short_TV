alter table ai_video_task add column request_hash varchar(64) null;
alter table ai_video_task add column poll_retry_count int not null default 0;
alter table ai_video_task add column last_poll_at datetime null;
alter table ai_video_task add column next_poll_at datetime null;

create index idx_ai_video_task_request_hash on ai_video_task (tenant_id, project_id, request_hash);
create index idx_ai_video_task_next_poll on ai_video_task (status, next_poll_at);
