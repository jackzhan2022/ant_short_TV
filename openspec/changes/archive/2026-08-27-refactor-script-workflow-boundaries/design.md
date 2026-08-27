## Context

The current `ScriptWorkflowService` is the main backend service for script generation, editing, rewriting, element extraction, element CRUD, element confirmation, storyboard breakdown, and workspace assembly. Its script element path mixes several separate responsibilities:

- validating access and permissions
- building AI extraction prompts
- calling the AI gateway
- parsing extraction JSON
- replacing unconfirmed draft elements
- matching new elements to confirmed elements by name
- confirming new elements or merging pending review records into confirmed targets

This makes the extraction path hard to test in isolation. It also hides the most important business rule: AI extraction can prepare replacement drafts, but confirmed project assets are changed only after explicit user confirmation.

## Goals / Non-Goals

**Goals:**

- Keep `ScriptWorkflowService` as the project script workflow facade used by `ScriptWorkflowController`.
- Move AI element extraction into a focused service that owns prompt construction, AI gateway invocation, response parsing, and normalized extraction results.
- Move draft persistence into a focused service that owns soft-deleting unconfirmed elements for the selected type and inserting the new draft set.
- Move confirmation and name-based merge updates into a focused service that owns `PENDING_REVIEW`, `merge_target_id`, confirmed target updates, and draft cleanup.
- Preserve existing endpoints, request/response DTOs, permissions, and database schema.
- Add focused tests for extraction, draft replacement, merge targeting, and confirmation.

**Non-Goals:**

- Do not redesign the script workspace UI.
- Do not change the element response contract or generated frontend API files.
- Do not introduce new tables or alter existing element status names.
- Do not change AI provider selection or point charging behavior outside the existing element extraction flow.
- Do not refactor storyboard, script generation, rewrite, or prompt generation workflows in this change.

## Decisions

### Keep `ScriptWorkflowService` as a facade

`ScriptWorkflowService.extractElements(...)` will continue to perform tenant resolution, project access checks, permission checks, and current script validation. After validation it delegates to the new element workflow services and then returns `workspace(tenantId, projectId)`.

Alternative considered: move the endpoint directly to a new controller/service pair. That would reduce the facade size further, but it would also require route ownership changes and broader controller tests. This change is intended to be a low-risk boundary refactor.

### Introduce typed extraction results before persistence

`ScriptElementExtractionService` will return typed extraction results rather than exposing raw `JsonNode` to persistence. The result can contain element type plus normalized records for character, scene, and prop extraction. Prompt builders and JSON parsing helpers move out of the facade.

Alternative considered: keep `JsonNode` as the boundary. That would minimize code movement, but it keeps persistence coupled to provider response shape and makes tests less direct.

### Separate draft replacement from merge confirmation

`ScriptElementDraftService` will own the extraction write path:

- soft-delete unconfirmed rows for the selected element type in the current tenant/project
- find a confirmed row with the same normalized name in the same tenant/project/type
- insert the extracted row as `DRAFT` when no target exists
- insert the extracted row as `PENDING_REVIEW` with `merge_target_id` when a confirmed target exists

`ScriptElementConfirmationService` will own the confirmation write path:

- if the selected row is `PENDING_REVIEW` and has `merge_target_id`, update the confirmed target from the pending row and soft-delete the pending row
- otherwise mark the selected row `CONFIRMED` and clear `merge_target_id`

Alternative considered: combine draft replacement and confirmation into one repository-like class. That reduces file count, but it keeps two different lifecycle phases tangled together.

### Use existing database access first

The refactor should initially keep the existing `JdbcTemplate` SQL and current tables. SQL may move into focused services, but schema behavior should not change.

Alternative considered: introduce MyBatis mappers for `character_asset`, `scene_asset`, and `prop_asset`. That may be useful later, but doing it together with the service split increases the blast radius without being required for the boundary fix.

## Risks / Trade-offs

- [Risk] Moving SQL can accidentally change field mapping or status behavior. -> Mitigation: add focused tests around each element type and confirmation branch before or alongside the refactor.
- [Risk] Extraction may still report AI success while persistence fails. -> Mitigation: keep extraction and draft persistence in the same transactional facade method and test that successful responses are visible in the returned workspace.
- [Risk] Name-based matching can remain too strict if names differ by whitespace or case. -> Mitigation: preserve current exact-name behavior for compatibility, but centralize the matching method so future normalization can be changed in one place.
- [Risk] `ScriptWorkflowService` remains large after this change. -> Mitigation: limit this change to element extraction and confirmation boundaries, then consider storyboard/script-generation splits separately.

## Migration Plan

No database migration is planned. Deploy as an internal backend refactor with existing API compatibility.

Rollback is code-only: revert the service split and route `ScriptWorkflowService` back to the previous private helper methods.

## Open Questions

None for this change. The current confirmed decisions are: draft elements are replaced wholesale per extracted type, and name-based updates require explicit user confirmation.
