-- Local images use the same media delivery pipeline without an AI task or billing.
alter table ai_image_result modify column task_id bigint null;
