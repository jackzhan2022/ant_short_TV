alter table review_project
  add column main_project_id bigint null;

create index idx_review_project_tenant_main_project
  on review_project (tenant_id, main_project_id);
