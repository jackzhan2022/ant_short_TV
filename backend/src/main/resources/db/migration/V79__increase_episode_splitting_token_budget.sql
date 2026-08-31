update ai_workflow_agent
   set max_tokens = 32768,
       revision = revision + 1,
       updated_at = now()
 where code = 'short-drama-episode-splitting'
   and max_tokens = 16384;
