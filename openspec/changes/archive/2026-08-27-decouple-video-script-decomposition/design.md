## Context

The current video decomposition workflow is still project-scoped at the first step: uploads require `projectId`, batch creation requires `projectId`, storage paths include `/materials/{tenantId}/{projectId}/...`, and service authorization checks project access before any analysis can start. The desired product flow is different: users upload and analyze videos first, then choose the destination project only when confirming an edited draft import.

The existing schema stores `project_id` on batches and episodes as `not null`, so the backend must add a forward migration before new tenant-level batches can exist. Existing project-linked rows must continue to work.

## Goals / Non-Goals

**Goals:**

- Allow video upload and batch creation without a project ID.
- Generate tenant-scoped batch records and episode tasks from the submitted upload order.
- Preserve existing project-scoped decomposition rows and filters.
- Require and validate project access only when importing a draft into a project script version.
- Make upload network/service failures understandable in the frontend.

**Non-Goals:**

- No manual re-analysis button in this change.
- No changes to the existing production workbench loading pipeline.
- No generated OpenAPI service edits.
- No new object storage provider behavior beyond changing the decomposition upload key.

## Decisions

1. Make `video_decomposition_batch.project_id` and `video_decomposition_episode.project_id` nullable for new rows.

   Rationale: this is the smallest persistence change that supports tenant-level analysis while keeping historical rows intact. Alternatives considered were introducing a separate staging table or creating a placeholder project; both add more state and make the later import boundary less clear.

2. Store uploads under a tenant-level decomposition path.

   New uploads use a path like `/materials/{tenantId}/video-decomposition/{yyyyMMdd}/{uuid}.{ext}`. Batch creation validates that submitted videos belong to the current tenant-level decomposition path, not to a project material directory.

3. Keep project authorization at project-specific actions only.

   Upload, list, batch detail, episode detail, retry, and draft edit require active tenant membership. Confirm/import requires `projectId` in the request and validates `AI_SERVICE:USE` for that project before creating the `VIDEO_IMPORT` script/version.

4. Let confirmation bind the episode to the selected project.

   When confirmation succeeds, the service writes the target project ID to the episode and batch if they were previously unbound, creates the script version for that project, and returns a response containing the bound project ID. If an existing historical episode is already project-bound, confirmation must reject attempts to import it into a different project.

5. Remove project ID from the page's create/upload form.

   The independent `/video-script-decomposition` page shows batch name, model ID, upload ordering, progress, and detail review. The confirmation action collects/uses the target project ID at import time.

6. Improve no-response request errors globally.

   A browser-side request with `error.request` and no `error.response` is surfaced as a Chinese service/network unreachable message, while preserving offline handling and backend error payload handling.

## Risks / Trade-offs

- Nullable project IDs can expose tenant-level batch records more broadly within a tenant. Mitigation: list/detail access stays tenant-bound, and project-only actions still validate project permissions.
- Confirmation now needs a target project ID and may surprise existing callers. Mitigation: only upload/create are decoupled; confirm explicitly validates the new required field and historical project-bound confirms remain compatible when the same project is supplied or inferable.
- Storage path validation changes could reject older project-scoped upload metadata in tests or imports. Mitigation: accept both tenant-level decomposition paths for new uploads and historical project-scoped paths when the row/request is already project-bound.
