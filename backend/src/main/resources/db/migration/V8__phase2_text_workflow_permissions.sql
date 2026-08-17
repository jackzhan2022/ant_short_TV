insert ignore into permission
  (code, name, type, resource, action, created_at, updated_at)
values
  ('SCRIPT:AI_GENERATE', 'AI生成剧本', 'BUTTON', 'SCRIPT', 'AI_GENERATE', now(), now()),
  ('SCRIPT:AI_REWRITE', 'AI改写剧本', 'BUTTON', 'SCRIPT', 'AI_REWRITE', now(), now()),
  ('ELEMENT:VIEW', '查看元素库', 'PAGE', 'ELEMENT', 'VIEW', now(), now()),
  ('ELEMENT:AI_EXTRACT', 'AI提取元素', 'BUTTON', 'ELEMENT', 'AI_EXTRACT', now(), now()),
  ('ELEMENT:EDIT', '编辑元素', 'BUTTON', 'ELEMENT', 'EDIT', now(), now()),
  ('STORYBOARD:VIEW', '查看分镜', 'PAGE', 'STORYBOARD', 'VIEW', now(), now()),
  ('STORYBOARD:AI_BREAKDOWN', 'AI拆解分镜', 'BUTTON', 'STORYBOARD', 'AI_BREAKDOWN', now(), now()),
  ('STORYBOARD:EDIT', '编辑分镜', 'BUTTON', 'STORYBOARD', 'EDIT', now(), now()),
  ('PROMPT:AI_GENERATE', 'AI生成提示词', 'BUTTON', 'PROMPT', 'AI_GENERATE', now(), now());

insert ignore into role_permission (role_id, permission_id, created_at)
select r.id, p.id, now()
  from `role` r
  join permission p on p.code in (
    'SCRIPT:AI_GENERATE',
    'SCRIPT:AI_REWRITE',
    'ELEMENT:VIEW',
    'ELEMENT:AI_EXTRACT',
    'ELEMENT:EDIT',
    'STORYBOARD:VIEW',
    'STORYBOARD:AI_BREAKDOWN',
    'STORYBOARD:EDIT',
    'PROMPT:AI_GENERATE'
  )
 where r.code in ('OWNER', 'ADMIN')
   and r.deleted_at is null;
