# Visual Gallery Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine the visual gallery and add visible reference-image controls to the existing image-generation dialog.

**Architecture:** Keep all state in the settings page and use existing visual-variant and image-task APIs. The generation dialog derives a reference image from the visual asset's primary image, while model options and defaults come from the project AI configuration APIs.

**Tech Stack:** React 19, TypeScript, Ant Design 6, Vitest, Testing Library.

---

### Task 1: Cover reference-image generation behavior

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.test.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`

- [ ] **Step 1: Add a failing test**

Open a character visual gallery, select a non-primary variant, open “生成图片”, and assert that its dialog contains the primary-image reference preview. Submit with a selected model, 3:4 ratio, and count 1; assert `createAiImageTask` receives `referenceImages`, `modelId`, `aspectRatio`, and `imageCount`.

- [ ] **Step 2: Run the focused test and observe failure**

Run: `npm --prefix frontend test -- src/pages/projects/production-workbench/settings.test.tsx`

Expected: FAIL because the generation dialog has no visible reference image or model selection.

- [ ] **Step 3: Implement the generation controls**

Load project image models and current image-model selection when the generator opens. Render current and reference previews, a model select, ratio select, count input, and an estimated-points label. Include the selected model and values in the existing create-image-task call.

- [ ] **Step 4: Run the focused test and confirm it passes**

Run: `npm --prefix frontend test -- src/pages/projects/production-workbench/settings.test.tsx`

Expected: PASS.

### Task 2: Polish the visual gallery hierarchy

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`
- Test: `frontend/src/pages/projects/production-workbench/settings.test.tsx`

- [ ] **Step 1: Add a failing gallery assertion**

Assert that the gallery renders a dedicated primary thumbnail and an “新增变装” tile after the variant thumbnails.

- [ ] **Step 2: Run the focused test and observe failure**

Run: `npm --prefix frontend test -- src/pages/projects/production-workbench/settings.test.tsx`

Expected: FAIL until the explicit gallery labels are available.

- [ ] **Step 3: Implement the visual hierarchy**

Move the creation input behind the final add tile, give the active large preview the visual priority, and retain horizontal thumbnail scrolling, selection, arrows, status, episode metadata, primary action, and deletion.

- [ ] **Step 4: Run focused tests and static checks**

Run: `npm --prefix frontend test -- src/pages/projects/production-workbench/settings.test.tsx && npm --prefix frontend run tsc && npm --prefix frontend run biome:lint`

Expected: tests and type check pass; report unrelated existing lint warnings separately.
