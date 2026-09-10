# Workbench Header Dialogs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add model configuration, original-script viewing, and project-name editing dialogs to the production-workbench header.

**Architecture:** Keep the workbench shell as the entry point. Extract the existing AI configuration selection surface into a reusable component consumed by both the existing page and a header modal. The header loads script workspace only when the user requests the source dialog and updates project state from the existing project update response.

**Tech Stack:** React 19, TypeScript, Umi Max, Ant Design 6, Vitest and Testing Library.

---

### Task 1: Cover the header dialog contract

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/index.test.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/index.tsx`

- [ ] **Step 1: Write failing tests for each header operation**

Add mocks for `updateProject`, `queryScriptWorkspace`, and the model configuration services. Assert that clicking “AI 模型” shows a dialog rather than calling `history.push`; clicking “查看原文” displays all returned `script.content`; clicking “编辑项目名” shows the current name, rejects an empty value, and calls `updateProject` with the existing project fields and new trimmed name.

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `npm --prefix frontend test -- src/pages/projects/production-workbench/index.test.tsx`

Expected: FAIL because the header lacks dialogs and the edit control has no action.

- [ ] **Step 3: Implement the minimal header dialog state and actions**

In `index.tsx`, add independent open/loading/saving state for the three dialogs. Use `Modal` and `Input` for project-name editing; call `queryScriptWorkspace(projectId)` only when opening the source dialog; use a read-only `Typography.Paragraph` with `whiteSpace: 'pre-wrap'` inside a scrollable dialog body. On successful `updateProject`, replace the header project state with `response.data`.

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `npm --prefix frontend test -- src/pages/projects/production-workbench/index.test.tsx`

Expected: PASS.

### Task 2: Reuse AI model selection inside the modal

**Files:**
- Create: `frontend/src/pages/projects/production-workbench/ai-config/ProjectAiConfigPanel.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/ai-config/index.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/index.tsx`
- Test: `frontend/src/pages/projects/production-workbench/index.test.tsx`

- [ ] **Step 1: Write the failing modal assertion**

Extend the header test to resolve text/image/video/audio model responses, click “AI 模型”, and assert the dialog exposes “文本模型” and “保存配置”.

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `npm --prefix frontend test -- src/pages/projects/production-workbench/index.test.tsx`

Expected: FAIL because no model configuration content is rendered in the header dialog.

- [ ] **Step 3: Extract and render the reusable panel**

Move the model sections, loading, selection, permission calculation, and save behavior from `ai-config/index.tsx` to `ProjectAiConfigPanel.tsx`. Give it `projectId` and `compact` props; `compact` omits the page title while retaining model cards and the save button. Render it in a `Modal` in the workbench header and render the same component from the full page.

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `npm --prefix frontend test -- src/pages/projects/production-workbench/index.test.tsx`

Expected: PASS.

### Task 3: Verify quality and commit

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/index.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/index.test.tsx`
- Create: `frontend/src/pages/projects/production-workbench/ai-config/ProjectAiConfigPanel.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/ai-config/index.tsx`

- [ ] **Step 1: Run affected page tests**

Run: `npm --prefix frontend test -- src/pages/projects/production-workbench/index.test.tsx src/pages/projects/production-workbench/ai-config/index.test.tsx`

Expected: PASS.

- [ ] **Step 2: Run type and formatting checks**

Run: `npm --prefix frontend run tsc` and `npm --prefix frontend run biome:lint`

Expected: both commands exit 0.

- [ ] **Step 3: Commit the focused implementation**

Run: `git add frontend/src/pages/projects/production-workbench/index.tsx frontend/src/pages/projects/production-workbench/index.test.tsx frontend/src/pages/projects/production-workbench/ai-config/index.tsx frontend/src/pages/projects/production-workbench/ai-config/ProjectAiConfigPanel.tsx && git commit -m "feat(workbench): add header dialogs"`
