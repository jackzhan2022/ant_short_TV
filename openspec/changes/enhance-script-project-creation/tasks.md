## 1. Backend Project Contract

- [x] 1.1 Add a project metadata migration for aspect ratio, file format, script type, breakdown strength, cover source, visual style, and optional initial script content reference fields.
- [x] 1.2 Extend `ProjectEntity`, `ProjectResponse`, `CreateProjectRequest`, and `UpdateProjectRequest` with the new optional initialization fields.
- [x] 1.3 Update `ProjectService` create/update mapping so legacy requests still succeed and new fields are persisted and returned.
- [x] 1.4 Add backend tests for project creation with new initialization fields and for legacy creation without them.

## 2. Frontend Creation Flow

- [x] 2.1 Add a dedicated `短剧创作` top-level menu route and make the existing project creation shortcut open that page.
- [x] 2.2 Build the first step with domestic/overseas tabs, script paste area, supported file upload entry, skip-script action, and inspiration gallery grid.
- [x] 2.3 Preserve script draft state when users switch inspiration categories or continue without uploading a script.
- [x] 2.4 Build the second step with aspect ratio, file format, script type, breakdown strength, cover upload/default behavior, visual style selection, point-cost display, back navigation, and create action.
- [x] 2.5 Submit the accumulated creation state through the existing `createProject` service and navigate to the created project's production workbench on success.

## 3. Shared Types and UI Data

- [x] 3.1 Extend `ProjectFormValues` and `Project` frontend types with the new initialization fields.
- [x] 3.2 Add typed constants for aspect ratio, file format, script type, breakdown strength, and visual style options used by the creation flow.
- [x] 3.3 Wire the inspiration gallery to existing curated data or an existing browse API without persisting unselected inspiration items into the project payload.

## 4. Verification

- [x] 4.1 Add frontend tests for opening the `短剧创作` menu entry, skipping script input, preserving pasted script text, selecting initialization options, and submitting through `createProject`.
- [x] 4.2 Add route or component tests for navigation from creation success into the production workbench.
- [x] 4.3 Run impacted backend tests for project creation and required frontend checks for TypeScript and component tests.
