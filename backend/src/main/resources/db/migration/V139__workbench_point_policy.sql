-- Preserve previously settled amounts when an existing analysis execution is retried.
-- NULL denotes a historical reservation; new workbench reservations start at zero.
alter table ai_point_reservation
  add column workbench_baseline_points decimal(24,8) null;

-- Remember capped/waived usage too, so refunds never rebill previously processed usage.
alter table ai_point_reservation
  add column workbench_assessed_points decimal(24,8) null;

-- Freeze server-resolved scope on reservations, so cap reads never depend on an
-- older execution-table snapshot inside a locking subquery.
alter table ai_point_reservation add column workbench_project_id bigint null;
update ai_point_reservation
   set workbench_project_id = (
       select e.project_id from ai_execution_task e
        where e.id = ai_point_reservation.execution_id
          and e.tenant_id = ai_point_reservation.tenant_id
          and e.project_id is not null and e.capability = 'TEXT'
          and e.scene in ('script_analysis','script_episode_split','script_episode_summary',
                         'script_character_scene_recognition','scoped_asset_reextraction','storyboard_breakdown')
   );
create index idx_ai_point_workbench_project on ai_point_reservation(tenant_id,workbench_project_id,id);
