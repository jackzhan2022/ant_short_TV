## 1. Backend Episode Parsing

- [x] 1.1 Add an episode response model and include `episodes` in `ScriptWorkspaceResponse` without removing existing fields.
- [x] 1.2 Implement a deterministic backend parser for `第N集`, Chinese numeral headings, and `EPNN` headings with optional titles and separator whitespace.
- [x] 1.3 Implement preamble preservation, heading removal from body content, ordering, duplicate handling, and one-episode fallback behavior.
- [x] 1.4 Add unit tests covering all supported heading formats, separators, preamble text, duplicate numbers, unstructured scripts, and blank scripts.
- [x] 1.5 Add controller/service coverage proving `script-workspace` returns parsed episodes for a persisted script.

## 2. Frontend Script Page

- [x] 2.1 Extend frontend workspace types with the episode response shape and retain compatibility when older responses omit `episodes`.
- [x] 2.2 Replace the fixed 24-episode list with backend-provided episodes and select the first available episode by default.
- [x] 2.3 Render each returned episode title and content without repeating the full script in empty tabs.
- [x] 2.4 Add frontend tests for dynamic episode count, episode content selection, and the legacy one-episode fallback.

## 3. Workbench Metadata And Team Points

- [x] 3.1 Add project metadata fields to the workbench project type and map known enum values to the existing human-readable labels.
- [x] 3.2 Load the active tenant's global point account through the existing points service and display the returned balance in the workbench header.
- [x] 3.3 Replace hard-coded project-specific labels for aspect ratio, file format, script type, breakdown strength, and visual style with response-driven values.
- [x] 3.4 Keep unsupported static fields such as `720p`, navigation labels, and the platform disclaimer unchanged.
- [x] 3.5 Add frontend tests for metadata mapping, balance rendering, and point-request failure isolation.

## 4. Verification

- [x] 4.1 Run focused backend tests for script workflow and project creation behavior.
- [x] 4.2 Run focused frontend tests for the production workbench script and shell.
- [x] 4.3 Run backend compilation and frontend lint/type checks.
- [x] 4.4 Review the final diff for API compatibility and confirm no database migration is required.
