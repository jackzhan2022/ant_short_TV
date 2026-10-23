-- Menu visibility is independent from business actions and project data access.
insert into permission (code, name, type, resource, action, created_at, updated_at) values
  ('MENU:SHORT_DRAMA_CREATION', '短剧创作', 'MENU', 'MENU_CREATION', 'SHORT_DRAMA_CREATION', current_timestamp, current_timestamp),
  ('MENU:FREE_CREATION', '自由创作', 'MENU', 'MENU_CREATION', 'FREE_CREATION', current_timestamp, current_timestamp),
  ('MENU:VIDEO_DECOMPOSITION', '视频拆剧', 'MENU', 'MENU_CREATION', 'VIDEO_DECOMPOSITION', current_timestamp, current_timestamp),
  ('MENU:SCRIPT_REVIEW', '剧本审核', 'MENU', 'MENU_CREATION', 'SCRIPT_REVIEW', current_timestamp, current_timestamp),
  ('MENU:PROJECTS', '项目中心', 'MENU', 'MENU_WORKSPACE', 'PROJECTS', current_timestamp, current_timestamp),
  ('MENU:ASSET_LIBRARY', '资产库', 'MENU', 'MENU_WORKSPACE', 'ASSET_LIBRARY', current_timestamp, current_timestamp),
  ('MENU:PRODUCTION_TASKS', '任务中心', 'MENU', 'MENU_WORKSPACE', 'PRODUCTION_TASKS', current_timestamp, current_timestamp),
  ('MENU:DRAMA_LIBRARY', '剧集管理', 'MENU', 'MENU_MANAGEMENT', 'DRAMA_LIBRARY', current_timestamp, current_timestamp),
  ('MENU:TEAM_MANAGEMENT', '团队管理', 'MENU', 'MENU_MANAGEMENT', 'TEAM_MANAGEMENT', current_timestamp, current_timestamp),
  ('MENU:AI_MANAGEMENT', '管理后台', 'MENU', 'MENU_MANAGEMENT', 'AI_MANAGEMENT', current_timestamp, current_timestamp);

-- Preserve existing navigation when upgrading. Subsequent role saves may revoke menus.
-- Existing video AI usage and platform-only drama restrictions still apply.
insert into role_permission (role_id, permission_id, created_at)
select r.id, p.id, current_timestamp
from `role` r
cross join permission p
where r.deleted_at is null and p.type = 'MENU' and p.code like 'MENU:%'
  and not exists (
    select 1 from role_permission rp where rp.role_id = r.id and rp.permission_id = p.id
  );
