## Context

The current project creation entry in `frontend/src/pages/projects/list/index.tsx` uses a single modal form with only the core project fields: name, code, organization, owner, description, cover URL, and start/end dates. That is sufficient for generic CRUD, but it does not match the short-drama creation flow shown in the reference screens. The product also needs a dedicated menu entry named `短剧创作`, so users can start creation directly from the main navigation instead of first entering the project list.

The reference UX has two distinct phases:
1. An inspiration-first landing step where the user can paste or upload script content, or skip input entirely and browse an inspiration gallery.
2. A configuration step where the user selects project initialization attributes such as aspect ratio, file format, script type, breakdown strength, cover image, and visual style.

The backend already exposes `POST /api/projects` through `createProject`, so this change should reuse the existing project creation endpoint instead of inventing a new creation API. The main work is therefore a contract expansion for the project payload and a frontend flow redesign around the existing submit path.

## Goals / Non-Goals

**Goals:**
- Turn project creation into a guided two-step flow aligned with the provided reference screens.
- Add an independent `短剧创作` menu route as the primary entry for this flow.
- Reuse the existing `createProject` endpoint as the final submission path.
- Add the missing project initialization fields needed by the new creation experience.
- Keep the existing project list/detail/workbench routing model intact.
- Provide a page where users can start from a script, skip script input, and browse the inspiration gallery before submitting.

**Non-Goals:**
- Do not redesign the production workbench itself.
- Do not change the project list or detail page beyond what is required to enter the new flow.
- Do not introduce a separate project creation endpoint unless the existing contract proves insufficient.
- Do not build a full script editor or media-generation engine in this change.

## Decisions

### 1. Keep `createProject` as the authoritative submission API

The flow will remain anchored on the existing project creation endpoint and request shape, with new fields added to the payload and backend model as needed. The frontend may collect data in multiple steps, but it will still submit a single project creation request at the end.

Alternatives considered:
- Introduce a dedicated multi-step draft API: rejected because it adds a new lifecycle that is not required for the first version.
- Split into separate script and project endpoints: rejected because the user asked to reuse the current project creation interface.

### 2. Add a dedicated `短剧创作` route and menu entry

Add a first-class route such as `/short-drama-creation` with menu name `short-drama-creation` and locale label `短剧创作`. The route will render the creation page and include the inspiration gallery list directly under the script intake area, matching the first reference screen.

Alternatives considered:
- Keep creation only inside the project list toolbar: rejected because the user explicitly wants an independent menu entry.
- Nest the entry under `项目中心`: rejected because the intended entry is task-oriented creation, not project administration.

### 3. Model the new UX as a two-step creator rather than a modal form extension

The first screen will focus on inspiration and script entry/skip actions. The second screen will contain the project configuration form. This matches the reference interaction model more closely than stuffing all fields into one dense modal.

Alternatives considered:
- Keep a single large modal: rejected because it would mix inspiration browsing with configuration and be awkward on desktop.
- Make a wizard inside the existing list page modal: rejected because the inspiration gallery and configuration step need more room than the current modal affordance provides.

### 4. Extend the project payload with creation-time creative metadata

The project request model will gain the fields necessary for the new configuration step, such as aspect ratio, file format, script type, breakdown strength, cover source, and visual style selections. These fields are creation-time defaults and can seed later workbench screens.

Alternatives considered:
- Keep these values only in frontend state: rejected because they would be lost after submission and could not be used by downstream workflows.
- Store them in a separate auxiliary table: rejected for the first iteration because it increases query complexity without clear benefit.

### 5. Treat inspiration content as browse-time UI data, not project state

The inspiration gallery should be rendered under the `短剧创作` page from a curated local dataset or existing browse API, but it should not become part of the persisted project record unless the user explicitly selects something that must be saved.

Alternatives considered:
- Persist the full inspiration item into the project: rejected because the user mainly needs browsing and selection guidance.
- Load inspiration from the same project table: rejected because inspiration is platform content, not project content.

### 6. Add the new fields with backward-compatible defaults

Existing project creation callers should continue to work. New fields must be optional at the API boundary, with sensible defaults applied when the creator submits the new flow.

## Risks / Trade-offs

- [Wider request payload] -> The creation request becomes larger. Mitigation: keep creative fields optional and only add fields that are used by the new flow.
- [Backend and frontend drift] -> The new form fields can diverge from the request model. Mitigation: update shared types and add contract tests around project creation.
- [More complex onboarding] -> A two-step flow can feel heavier than a quick form. Mitigation: make script skip explicit and keep the second step focused.
- [Navigation duplication] -> Users may see both project list creation and short-drama creation. Mitigation: make `短剧创作` the primary creation path and keep project list creation only as a secondary shortcut or redirect into the same flow.

## Migration Plan

1. Extend the project request/response model and database schema only as far as required for the new creative metadata.
2. Update frontend shared types so the creation flow can hold the full payload in one place.
3. Add the `短剧创作` route, menu locale, and access configuration.
4. Replace or redirect the current create-project modal entry so it opens the same two-step creation page.
5. Add tests for the new flow, field defaults, and project creation submission.
6. Verify that old project creation callers still succeed with missing optional fields.

Rollback:
- If the new flow needs to be backed out, hide the `短剧创作` route/menu item, revert the frontend shortcut to the existing modal, and keep the new backend fields optional so old clients continue to work.

## Open Questions

- Which exact creative metadata fields should be persisted on the project versus kept as transient creation-step state?
- Should the inspiration gallery be fully local/curated for now, or should it consume an existing browse endpoint already present in the project?
- Which access permission should gate the `短剧创作` menu item: reuse `canUseProjectCenter` or add a dedicated creation permission?
