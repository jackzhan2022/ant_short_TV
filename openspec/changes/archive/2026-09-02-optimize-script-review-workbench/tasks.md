## 1. Route and project-library foundation

- [x] 1.1 Inspect the existing script-review route, menu conventions, and review-project service types; add a dedicated script-review library route and navigation entry without breaking direct workbench access.
- [x] 1.2 Build the project-list data adapter that derives display-only work states, outstanding issue counts, and next-step labels from existing project, task, and issue data.
- [x] 1.3 Implement the responsive script-review library list with project search, derived-state filtering, version/round metadata, and a link that passes the selected project ID to the workbench.
- [x] 1.4 Replace the existing inline independent-script import form with a library modal containing project name, supported upload, pasted content, validation, and fixed confirmation actions.
- [x] 1.5 On successful import, close the modal, preserve the library's client-side context until navigation, refresh projects, and open the newly created project's workbench; retain list state when the modal is cancelled.

## 2. Review workbench layout

- [x] 2.1 Update the workbench loader to honor a selected project ID from navigation while preserving its existing default-project behavior.
- [x] 2.2 Refactor the desktop review view into a problem queue, script content area, and selected-problem detail panel while preserving version selection, save, rollback, export, task cancellation, and retry actions.
- [x] 2.3 Implement issue queue filtering, selected-issue state, processed issue access, and visible problem summary counts using the current review-task response.
- [x] 2.4 Implement safe issue-hit focus and highlighting in the current script editor, including an explicit unavailable-location state when a text match cannot be found.
- [x] 2.5 Add responsive behavior that exposes the issue queue and detail actions without obscuring script content on constrained viewports.

## 3. Task creation and issue actions

- [x] 3.1 Move review mode, dimension, and scope controls into an on-demand task-configuration modal that reuses the current task-creation request contract and validation rules, preserving the underlying workbench context when closed.
- [x] 3.2 Show the existing AI execution progress and task state after a task is created, canceled, retried, or selected from history.
- [x] 3.3 Add a batch-repair preview that identifies selected hits and the supported replacement effect before invoking the existing repair request.
- [x] 3.4 Keep manual resolution reachable from the selected issue detail and refresh the queue and derived status after completion.

## 4. Verification

- [x] 4.1 Add focused tests for project-state derivation, library filtering, and project-ID navigation to the workbench.
- [x] 4.2 Add component tests for task-drawer validation, issue selection, matching-hit focus, and unavailable-location behavior.
- [x] 4.3 Run the relevant frontend test suite, `npm run lint`, and `npx antd lint ./src` from `frontend`; fix regressions introduced by this change.
