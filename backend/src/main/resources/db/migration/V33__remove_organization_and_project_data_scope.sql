delete from role_permission
where permission_id in (
  select id from permission where resource = 'ORGANIZATION' or code like 'ORGANIZATION:%'
);

delete from project_role_permission
where permission_id in (
  select id from permission where resource = 'ORGANIZATION' or code like 'ORGANIZATION:%'
);

delete from permission where resource = 'ORGANIZATION' or code like 'ORGANIZATION:%';

drop table organization_member;
drop table organization;

alter table project drop index idx_project_tenant_organization;
alter table project drop column organization_id;
alter table project_member drop column organization_id;
alter table project_role drop column data_scope;
