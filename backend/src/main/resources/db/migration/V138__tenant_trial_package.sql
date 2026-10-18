alter table team_point_account
  add column trial_balance decimal(24,8) not null default 0;

alter table ai_point_reservation
  add column trial_eligible boolean not null default false;
alter table ai_point_reservation
  add column trial_reserved_points decimal(24,8) not null default 0;
alter table ai_point_reservation
  add column trial_settled_points decimal(24,8) not null default 0;

insert into commercial_package (code,package_type,status,created_at,updated_at)
values ('TENANT_TRIAL','TRIAL','ACTIVE',now(),now());
insert into commercial_package_version
  (package_id,version_no,name,description,price,currency,effective_from,status,created_at)
select id,1,'试用套餐','3000 专属积分，不可用于视频生成模型',0,'CNY',now(),'INTERNAL',now()
from commercial_package where code='TENANT_TRIAL';

insert into platform_permission (code,name,resource,action,created_at,updated_at)
values ('PLATFORM_TENANT_TRIAL_ASSIGN','Assign tenant trial package','PLATFORM_TENANT','TRIAL_ASSIGN',now(),now());
insert into platform_role_permission (role_id,permission_id,created_at)
select r.id,p.id,now() from platform_role r join platform_permission p
on p.code='PLATFORM_TENANT_TRIAL_ASSIGN' where r.code='PLATFORM_ADMIN';
