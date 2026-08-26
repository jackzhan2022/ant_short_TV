alter table ai_execution_task add column commercial_subscription_id bigint null;
alter table ai_execution_task add column commercial_package_version_id bigint null;
alter table ai_execution_task add column pre_discount_points decimal(24,8) null;
alter table ai_execution_task add column discount_rate decimal(24,8) not null default 1;
alter table ai_execution_task add column final_points decimal(24,8) null;
alter table ai_point_reservation add column discount_rate decimal(24,8) not null default 1;
