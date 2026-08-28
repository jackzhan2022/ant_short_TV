## 1. Extract Agent and Skill content

- [x] 1.1 Refactor `frontend/src/pages/ai-service-management/agents/index.tsx` to export reusable Agent and Skill tab content plus existing detail interactions without nested tabs
- [x] 1.2 Keep the standalone Agent page test coverage passing for list, detail, and Prompt preview behavior after extraction

## 2. Extend model management tabs

- [x] 2.1 Add permission-aware `agents` and `skills` tabs after logs in `frontend/src/pages/ai-service-management/model-management/index.tsx`
- [x] 2.2 Add model-management tests for five same-level tabs, default authorized tab selection, and Agent/Skill content switching
- [x] 2.3 Verify unauthorized Agent/Skill tabs are omitted while existing Provider/Model/Logs permission behavior remains unchanged

## 3. Update routing and compatibility

- [x] 3.1 Change `/ai-service-management/agents` to a hidden redirect targeting `/ai-service-management/model-management`
- [x] 3.2 Update route tests and menu locale assertions for the consolidated navigation

## 4. Verification

- [x] 4.1 Run model management, Agent/Skill, and route frontend tests
- [x] 4.2 Run frontend lint and TypeScript checks
- [x] 4.3 Run OpenSpec validation for the modified navigation capability
