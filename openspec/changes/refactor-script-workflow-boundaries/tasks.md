## 1. Baseline and Regression Tests

- [x] 1.1 Review current `ScriptWorkflowService` element extraction, draft replacement, merge target lookup, and confirmation SQL paths.
- [x] 1.2 Add or update tests proving successful AI extraction returns populated characters, scenes, and props in the workspace.
- [x] 1.3 Add tests proving re-extraction soft-deletes only unconfirmed rows for the requested element type and preserves confirmed rows.
- [x] 1.4 Add tests proving extracted elements with matching confirmed names become `PENDING_REVIEW` with `merge_target_id`.
- [x] 1.5 Add tests proving merge target lookup is isolated by tenant, project, and element type.
- [x] 1.6 Add tests proving user confirmation updates the merge target and soft-deletes the pending row, while standalone drafts become `CONFIRMED`.

## 2. Extraction Boundary

- [x] 2.1 Create typed script element extraction result models for character, scene, and prop records.
- [x] 2.2 Create `ScriptElementExtractionService` and move element extraction prompt construction into it.
- [x] 2.3 Move AI gateway invocation and extraction JSON parsing into `ScriptElementExtractionService`.
- [x] 2.4 Update `ScriptWorkflowService.extractElements` to delegate AI extraction by element type after existing access and script validation.

## 3. Draft Persistence Boundary

- [x] 3.1 Create `ScriptElementDraftService` for replacing extracted drafts.
- [x] 3.2 Move unconfirmed-row cleanup into `ScriptElementDraftService`.
- [x] 3.3 Move confirmed-name lookup and `merge_target_id` assignment into `ScriptElementDraftService`.
- [x] 3.4 Move character, scene, and prop draft insert SQL into `ScriptElementDraftService`.
- [x] 3.5 Wire `ScriptWorkflowService.extractElements` so successful extraction persists through the draft service inside the existing transaction.

## 4. Confirmation Boundary

- [x] 4.1 Create `ScriptElementConfirmationService` for element confirmation and merge application.
- [x] 4.2 Move character confirmation and merge-update SQL into `ScriptElementConfirmationService`.
- [x] 4.3 Move scene confirmation and merge-update SQL into `ScriptElementConfirmationService`.
- [x] 4.4 Move prop confirmation and merge-update SQL into `ScriptElementConfirmationService`.
- [x] 4.5 Update `ScriptWorkflowService.confirmElement` to delegate confirmation after existing access and permission validation.

## 5. Cleanup and Verification

- [x] 5.1 Remove extraction, draft persistence, and confirmation private helpers from `ScriptWorkflowService` once delegated.
- [x] 5.2 Ensure endpoint request/response shapes and frontend service calls remain unchanged.
- [x] 5.3 Run targeted script workflow tests.
- [x] 5.4 Run the backend test suite or the broadest feasible Maven verification command.
- [x] 5.5 Update implementation notes or operational docs if the new service boundaries need maintenance guidance.
