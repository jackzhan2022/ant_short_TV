## Why

Workflow Agents can read one episode at a time, but cannot obtain a whole project's scripts with their episode boundaries intact. Tool selection also exposes only names and codes, forcing administrators to infer a tool's behavior before granting it to an Agent.

## What Changes

- Add the read-only `read_project_full_script` workflow tool, returning all valid episodes of the authorized project in episode order and preserving per-episode boundaries.
- Expose the existing tool description as hover help wherever an administrator selects, reviews, or inserts a workflow tool in the Agent editor.

## Capabilities

### New Capabilities

- `project-full-script-access`: Allows a workflow Agent to retrieve the complete current script set for its authorized project as structured, episode-bounded data.
- `workflow-tool-selection-help`: Provides concise tool descriptions at Agent tool-selection and prompt-insertion interactions.

### Modified Capabilities

None.

## Impact

- Backend workflow-tool catalog and screenplay data service.
- Agent runtime scope allowlist and tool catalog API response.
- Agent editor's allowed-tools selector, selected tool tags, and prompt helper buttons.
- Backend and frontend workflow-agent tests.
