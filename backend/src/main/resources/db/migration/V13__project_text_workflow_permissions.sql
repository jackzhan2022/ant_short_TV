insert ignore into permission
  (code, name, type, resource, action, created_at, updated_at)
values
  ('SCRIPT:VIEW', '查看剧本', 'PAGE', 'SCRIPT', 'VIEW', now(), now()),
  ('SCRIPT:CREATE', '创建剧本', 'BUTTON', 'SCRIPT', 'CREATE', now(), now()),
  ('SCRIPT:EDIT', '编辑剧本', 'BUTTON', 'SCRIPT', 'EDIT', now(), now()),
  ('SCRIPT:DELETE', '删除剧本', 'BUTTON', 'SCRIPT', 'DELETE', now(), now()),
  ('SCRIPT:AI_GENERATE', 'AI生成剧本', 'BUTTON', 'SCRIPT', 'AI_GENERATE', now(), now()),
  ('SCRIPT:AI_REWRITE', 'AI改写剧本', 'BUTTON', 'SCRIPT', 'AI_REWRITE', now(), now()),
  ('ELEMENT:VIEW', '查看元素库', 'PAGE', 'ELEMENT', 'VIEW', now(), now()),
  ('ELEMENT:AI_EXTRACT', 'AI提取元素', 'BUTTON', 'ELEMENT', 'AI_EXTRACT', now(), now()),
  ('ELEMENT:EDIT', '编辑元素', 'BUTTON', 'ELEMENT', 'EDIT', now(), now()),
  ('STORYBOARD:VIEW', '查看分镜', 'PAGE', 'STORYBOARD', 'VIEW', now(), now()),
  ('STORYBOARD:AI_BREAKDOWN', 'AI拆解分镜', 'BUTTON', 'STORYBOARD', 'AI_BREAKDOWN', now(), now()),
  ('STORYBOARD:EDIT', '编辑分镜', 'BUTTON', 'STORYBOARD', 'EDIT', now(), now()),
  ('PROMPT:AI_GENERATE', 'AI生成提示词', 'BUTTON', 'PROMPT', 'AI_GENERATE', now(), now()),
  ('AI_SERVICE:USE', '使用AI服务', 'BUTTON', 'AI_SERVICE', 'USE', now(), now());

insert ignore into project_role_permission (tenant_id, project_id, role_id, permission_id, created_at)
select pr.tenant_id, pr.project_id, pr.id, p.id, now()
  from project_role pr
  join permission p on p.code in (
    'SCRIPT:VIEW',
    'SCRIPT:CREATE',
    'SCRIPT:EDIT',
    'SCRIPT:DELETE',
    'SCRIPT:AI_GENERATE',
    'SCRIPT:AI_REWRITE',
    'ELEMENT:VIEW',
    'ELEMENT:AI_EXTRACT',
    'ELEMENT:EDIT',
    'STORYBOARD:VIEW',
    'STORYBOARD:AI_BREAKDOWN',
    'STORYBOARD:EDIT',
    'PROMPT:AI_GENERATE',
    'AI_SERVICE:USE'
  )
 where pr.code = 'PROJECT_OWNER'
   and pr.status = 'ACTIVE';
