update ai_workflow_agent
   set max_steps = 10,
       revision = revision + 1,
       updated_at = now()
 where code = 'short-drama-episode-splitting'
   and max_steps < 10;
