## 1. Scoped Recognition Contract

- [x] 1.1 Define shared `ALL` / `CHARACTER` / `SCENE` / `PROP` scope and `FILL_EMPTY` / `REGENERATE_ALL` prompt-policy types with request validation.
- [x] 1.2 Extend the formal scoped re-extraction operation, persisted snapshot/unit state, and execution handler to dispatch one asset-recognition Agent Run per active episode.
- [x] 1.3 Add authorized project APIs for scope preflight and scoped re-extraction submission, returning impact counts and an `AiExecutionResponse`.
- [x] 1.4 Add backend tests for authorization, validation, preflight counts, empty-scope direct submission, and confirmed scope submission.

## 2. Agent And Formal Persistence Scope

- [x] 2.1 Pass scope and prompt policy through workflow Agent input, Skill context, and trusted episode-read output.
- [x] 2.2 Extend `save_episode_assets` validation and persistence so single-type Runs accept only their canonical type and owned visual variants, while `ALL` retains all categories.
- [x] 2.3 Apply `FILL_EMPTY` and confirmed `REGENERATE_ALL` prompt writes server-side with type isolation and concurrency-safe conditions.
- [x] 2.4 Scope episode binding replacement and finalization so a single-type Run cannot write, retire, or unbind other types.
- [x] 2.5 Add focused Agent/tool/finalizer integration tests for each scope, full-scope compatibility, atomic rejection of out-of-scope payloads, prompt policy, and failed-unit no-retirement behavior.

## 3. Asset Settings Migration

- [x] 3.1 Add frontend service types and requests for scoped preflight and formal re-extraction; remove asset-settings use of the legacy `ai-extract-elements` request.
- [x] 3.2 Replace the asset-settings batch-generation action with preflight-driven direct submission or a confirmation modal containing impact counts and prompt-policy control.
- [x] 3.3 Poll and render scoped execution progress, reload affected settings data after success, and show terminal backend failure details after failure.
- [x] 3.4 Add frontend tests covering empty preflight, confirmation required, default fill-empty, explicit regeneration, scope selection, successful reload, and visible failure feedback.

## 4. Regression Verification

- [x] 4.1 Run targeted backend and frontend tests for scoped re-extraction, asset recognition, prompt lifecycle, and asset settings.
- [x] 4.2 Run the required repository checks and document any unrelated pre-existing failures. (Frontend `npm run lint` remains blocked by the pre-existing nested Biome config under `frontend/.legacy-rollback`.)
