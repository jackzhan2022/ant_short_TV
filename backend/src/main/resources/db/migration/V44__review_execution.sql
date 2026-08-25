insert into ai_point_policy_version
  (scene, model_id, capability, version_no, status, effective_from, effective_to,
   charge_provider_rejection, charge_provider_billed_failure, charge_timeout,
   charge_business_failure, created_at, published_at)
values
  ('script_review', null, 'TEXT', 1, 'PUBLISHED', '2000-01-01 00:00:00', null,
   false, true, true, true, now(), now());

insert into ai_point_policy_component
  (policy_version_id, metric, unit_size, point_rate, dimensions_json, dimensions_key, created_at)
select id, 'FIXED_EXECUTION', 1, 1, '{}', '', now()
  from ai_point_policy_version
 where scene = 'script_review' and version_no = 1;

create unique index uk_review_task_execution on review_task (execution_id);
