-- Leave settings unset until an administrator saves: deployment environment values remain effective.
create table ai_concurrency_config (
  id int primary key,
  team_text_limit int null,
  team_image_limit int null,
  team_video_limit int null,
  model_limit int null,
  provider_limit int null,
  text_workers int null,
  image_workers int null,
  video_submit_workers int null,
  video_query_workers int null,
  updated_at datetime not null
);

insert into ai_concurrency_config(id, updated_at) values (1, current_timestamp);

insert into platform_permission(code, name, resource, action, created_at, updated_at) values
  ('PLATFORM_AI_CONCURRENCY_VIEW', 'View AI concurrency configuration', 'PLATFORM_AI_CONCURRENCY', 'VIEW', current_timestamp, current_timestamp),
  ('PLATFORM_AI_CONCURRENCY_EDIT', 'Edit AI concurrency configuration', 'PLATFORM_AI_CONCURRENCY', 'EDIT', current_timestamp, current_timestamp);

insert into platform_role_permission(role_id, permission_id, created_at)
select role.id, permission.id, current_timestamp
from platform_role role
join platform_permission permission on permission.code in ('PLATFORM_AI_CONCURRENCY_VIEW', 'PLATFORM_AI_CONCURRENCY_EDIT')
where role.code = 'PLATFORM_ADMIN';

alter table ai_execution_task add column task_type varchar(16) not null default 'TEXT';

update ai_execution_task set task_type = case
  when upper(capability) = 'VIDEO_UNDERSTANDING' then 'TEXT'
  when upper(capability) = 'IMAGE' or lower(scene) = 'ai_image_generate'
    or upper(business_type) in ('AI_IMAGE', 'AI_IMAGE_TASK') then 'IMAGE'
  when upper(capability) = 'VIDEO' or lower(scene) = 'ai_video_generate'
    or upper(business_type) in ('AI_VIDEO', 'AI_VIDEO_TASK') then 'VIDEO'
  else 'TEXT'
end;

-- Rows serve as cross-instance admission mutexes for a tenant and modality.
create table ai_execution_tenant_quota (
  tenant_id bigint not null,
  task_type varchar(16) not null,
  primary key (tenant_id, task_type)
);

create index idx_ai_execution_typed_candidate on ai_execution_task(task_type, status, next_run_at, priority, created_at, id);
create index idx_ai_execution_typed_running on ai_execution_task(tenant_id, task_type, status, claim_expires_at);
create index idx_ai_video_task_due_poll on ai_video_task(status, next_poll_at, id);
