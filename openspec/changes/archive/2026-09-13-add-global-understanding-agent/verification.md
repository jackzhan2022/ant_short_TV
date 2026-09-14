# Online Smoke Verification

Date: 2026-09-13

An isolated `opsx-smoke-1789273347` tenant and project were created in the configured test-data environment. No credentials, script contents, or provider secrets are recorded here.

- First analysis: global-understanding Run `1267` succeeded with the required sequence `MODEL -> read_current_script -> MODEL -> save_global_understanding` and created formal row `9`.
- Independent reanalysis: after saving a revised script, Run `1270` succeeded. Formal row `9` was retained, its `last_agent_run_id` changed from `1267` to `1270`, and its analyzed-content hash changed.
- Stale-content protection: after Run `1273` successfully read the source, the script was edited before its save. The Run and analysis stage failed with `SCRIPT_CONTENT_CHANGED`; formal row `9` remained linked to Run `1270`.
- Retry protection: `POST /script-analysis/current/retry/GLOBAL_UNDERSTANDING` was accepted for the failed task and rejected its stale snapshot with `STALE_SCRIPT_VERSION`, without overwriting the formal row.
- Progress restoration: after a fresh login, `GET /script-page-workspace` returned persisted task `32` with `GLOBAL_UNDERSTANDING` pending state. That task later reached `SUCCEEDED` at 100%, Run `1274` succeeded, and formal row `9` advanced to Run `1274` with a new analyzed-content hash.
