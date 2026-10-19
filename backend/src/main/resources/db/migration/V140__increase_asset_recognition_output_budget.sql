-- Increase only the built-in legacy asset-recognition output budget.
-- Keep customized budgets and historical run snapshots unchanged.
update ai_workflow_agent
   set max_tokens = 32768,
       revision = revision + 1,
       updated_at = now()
 where code = 'short-drama-asset-recognition'
   and max_tokens in (4096, 16384);
