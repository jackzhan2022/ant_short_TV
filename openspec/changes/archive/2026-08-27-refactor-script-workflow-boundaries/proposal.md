## Why

`ScriptWorkflowService` currently owns script editing, AI element extraction, draft replacement, name-based merge matching, and user confirmation. This makes extraction failures hard to diagnose and makes the merge/confirmation rules easy to change accidentally while adding new AI workflows.

This change separates the script element workflow into clear backend boundaries while preserving the existing API surface and user-facing behavior.

## What Changes

- Split script element extraction out of `ScriptWorkflowService` into focused services for AI extraction, draft persistence, and confirmation.
- Keep existing project script APIs stable, including `/scripts/ai-extract-elements` and `/script-elements/{elementType}/{elementId}/confirm`.
- Preserve the current draft replacement rule: each extraction for a selected element type soft-deletes all unconfirmed elements of that type and inserts the new AI result set.
- Preserve and make explicit the name-based merge rule: extracted elements that match a confirmed element by tenant, project, type, and name become pending review records linked by `merge_target_id`.
- Preserve and make explicit the user confirmation rule: merge updates are applied only after the user confirms the pending element.
- Add focused tests around extraction persistence, name-based matching, confirmation merge behavior, and no cross-tenant/project/type merging.

## Capabilities

### New Capabilities

- `script-element-workflow-boundaries`: Covers the backend contract for AI script element extraction, draft replacement, name-based merge targeting, and explicit user confirmation.

### Modified Capabilities

None.

## Impact

- Backend script package:
  - `ScriptWorkflowService`
  - new script element extraction/draft/confirmation services
  - existing script workflow controller tests and new focused service tests
- No planned database schema changes.
- No planned frontend API contract changes.
- No generated frontend service files should be edited.
