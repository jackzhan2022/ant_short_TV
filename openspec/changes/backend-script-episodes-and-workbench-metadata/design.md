## Context

The production workbench currently loads the project and script workspace separately. The script page creates 24 episode tabs in the browser and falls back to rendering the complete script for every empty episode. The workbench header also contains project settings and a point balance that are hard-coded.

The backend already owns the canonical script, project metadata, tenant context, and team point account. The change should expose those existing facts through stable response fields without introducing a new persistence model for episodes.

## Goals / Non-Goals

**Goals:**

- Parse episode boundaries from the current script content on the backend.
- Return structured episodes from `script-workspace`.
- Keep parsing deterministic, tolerant of common Chinese and English episode headings, and safe for unstructured scripts.
- Make the production workbench display project metadata from the project detail API.
- Make the workbench display the current tenant's available team point balance.
- Add focused backend and frontend tests for parsing, fallback behavior, and dynamic rendering.

**Non-Goals:**

- Persist parsed episodes as separate database records.
- Rewrite or normalize the original script content.
- Add a project resolution field or a new point-account API.
- Change storyboard breakdown behavior or AI generation semantics.

## Decisions

### 1. Parse episodes in the backend response layer

The script workflow service will derive `episodes` from the current script content when constructing `ScriptWorkspaceResponse`. The parser will recognize heading lines for `第N集`, Chinese numerals such as `第一集`, and `EP01`, with optional title text separated by whitespace, colon, or Chinese colon.

Parsed episode objects will contain the numeric episode number, display title, and content. Heading lines will not be duplicated in content. Episodes will be ordered by episode number, and duplicate episode numbers will be merged into the first occurrence's section boundary.

Alternative considered: parsing in the frontend. Rejected because other clients and future workbench views would receive inconsistent episode behavior.

### 2. Use a single-episode fallback for unstructured scripts

If no supported heading is found, the backend will return one episode containing the full script content. This preserves visibility of imported prose or free-form notes without inventing 24 empty episodes.

Text before the first recognized heading will be included in the first episode's content so introductions and metadata are not lost.

### 3. Keep the original script and additive response compatibility

`ScriptWorkspaceResponse` will retain all existing fields and add `episodes`. Existing clients that ignore unknown JSON fields remain compatible. The frontend data type will make `episodes` optional during rollout and use a one-episode local fallback only when older backend responses omit it.

### 4. Load project metadata and team points independently in the workbench shell

The workbench shell will continue to query the project detail endpoint and additionally query the existing tenant point-account endpoint using the active tenant ID. Project metadata will map enum values to the existing creation-page labels. A point-account failure will display a neutral placeholder and must not block script workspace rendering.

Alternative considered: copying project metadata and points into `script-workspace`. Rejected because it would couple unrelated domain responses and duplicate the existing team point API.

### 5. Preserve fields without backend data

The UI will keep `720p`, platform disclaimer, and navigation labels static because there is no authoritative resolution field or dynamic content source for them in the current model. The hard-coded numeric point value will be removed.

## Risks / Trade-offs

- [Ambiguous headings] A line may look like an episode heading but be part of prose. -> Only treat a trimmed standalone line matching the supported heading grammar as a boundary.
- [Duplicate episode numbers] Repeated headings could produce confusing tabs. -> Merge repeated episode numbers into one episode section while preserving content order.
- [Legacy clients] Older clients may not understand `episodes`. -> Keep the response additive and retain frontend fallback handling.
- [Point query latency] The extra tenant query may delay the header. -> Load it independently and render a placeholder until it resolves; failure does not block the workbench.
- [Enum label drift] Frontend labels can diverge from backend enum values. -> Centralize the mapping in the workbench UI and use raw values as a fallback.

## Migration Plan

1. Add the episode response type and backend parser with unit tests.
2. Add `episodes` to the script workspace response and controller coverage.
3. Update frontend types and replace fixed episode generation with response-driven rendering.
4. Update the workbench shell and script page to consume project metadata and tenant point balance.
5. Run backend and frontend focused tests, then the project lint/type checks.

No database migration is required. Rollback consists of reverting the application changes; existing script and project data remains unchanged.

## Open Questions

- None for the initial implementation. The supported heading formats and fallback behavior are defined in the accompanying capability specifications.
