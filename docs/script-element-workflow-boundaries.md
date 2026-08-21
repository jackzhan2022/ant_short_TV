# Script Element Workflow Boundaries

`ScriptWorkflowService` remains the facade for project script APIs. It validates tenant membership, project access, permissions, element type, and current script content, then delegates script element work to focused services.

## Services

- `ScriptElementExtractionService`
  - Builds prompts for character, scene, and prop extraction.
  - Calls the text AI gateway with business scenes `character_extract`, `scene_extract`, and `prop_extract`.
  - Parses AI JSON into typed extraction result records.

- `ScriptElementDraftService`
  - Applies the extraction write rule inside the existing transactional facade call.
  - Soft-deletes unconfirmed rows for the requested element type.
  - Inserts the latest extracted rows.
  - Looks up confirmed rows by tenant, project, element type, and exact name.
  - Writes matched rows as `PENDING_REVIEW` with `merge_target_id`; unmatched rows as `DRAFT`.

- `ScriptElementConfirmationService`
  - Applies user confirmation.
  - For `PENDING_REVIEW` rows with `merge_target_id`, updates the linked confirmed row and soft-deletes the pending row.
  - For standalone drafts, marks the row `CONFIRMED` and clears `merge_target_id`.

## Rules To Preserve

- AI extraction success does not directly overwrite confirmed assets.
- Draft extraction replaces unconfirmed rows only for the extracted element type.
- Name-based merging is prepared during extraction but applied only after explicit user confirmation.
- Merge lookup must stay isolated by tenant, project, and element type.
- Existing controller endpoints and response DTOs are the compatibility boundary for the frontend.
