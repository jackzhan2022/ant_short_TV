create index idx_review_project_list_summary
  on review_project (tenant_id, deleted_at, updated_at, id);

create index idx_review_task_project_latest
  on review_task (tenant_id, project_id, created_at, id);
