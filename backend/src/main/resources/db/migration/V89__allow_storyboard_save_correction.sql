update ai_workflow_agent
   set max_steps = 14,
       revision = revision + 1,
       updated_at = now()
 where code = 'short-drama-storyboard'
   and max_steps < 14;
