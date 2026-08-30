## 1. Backend complete-script tool

- [x] 1.1 Add failing catalog and data-service tests for `read_project_full_script`, including schema validation, sorted episode records, current content, and an empty-project result.
- [x] 1.2 Register the read-only, empty-input `read_project_full_script` definition and its episode-array output schema.
- [x] 1.3 Implement the scoped full-project read by reusing the current episode-version selection path, and add the tool to the permitted read-tool scope list.
- [x] 1.4 Run the focused workflow-tool and scope-guard backend test suite.

## 2. Agent-editor tool descriptions

- [x] 2.1 Add failing Agent-editor tests for tool descriptions on option hover, selected tool values, and prompt-helper buttons.
- [x] 2.2 Render accessible Ant Design tooltip content from each tool's catalog description in the allowed-tools selector and its selected values.
- [x] 2.3 Render the same tooltip content on prompt-helper buttons without changing prompt insertion or tool authorization behavior.
- [x] 2.4 Run the focused Agent-editor frontend tests.

## 3. End-to-end verification

- [x] 3.1 Run backend tests covering the workflow tool registry, screenplay data service, and workflow Agent runner.
- [x] 3.2 Run frontend lint, focused tests, and production build.
- [ ] 3.3 Inspect the Agent editor manually to confirm the new tool can be selected and all tool descriptions appear on hover.
