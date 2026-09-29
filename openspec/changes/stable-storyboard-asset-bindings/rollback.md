## Rollback

This change uses the additive V122 `storyboard_asset_reference` table. Application rollback must retain that table and all binding rows.

1. Stop new storyboard generation and pause bounded backfill jobs.
2. Atomically point `/opt/antv/current` to the preceding compatible release and restart `antv.service`.
3. Keep V122, formal bindings, generated media, prompt documents, and synchronized legacy `characters`, `scene`, and `props` fields intact.
4. Verify the previous frontend loads and the previous backend returns its expected protected-API response.
5. Run the consistency audit. Do not delete or rewrite bindings to make legacy code appear current.

The prior application continues from the synchronized legacy display fields. Restoring the new application later reuses the retained formal bindings and can resume idempotent backfill. Database restoration is required only for an unrelated destructive failure; routine rollback does not restore or drop schema.
