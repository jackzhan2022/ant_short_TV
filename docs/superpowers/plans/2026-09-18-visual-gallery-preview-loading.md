# Visual Gallery Preview Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make gallery image changes immediate and stable, prevent progressive original-image painting, and remove the gallery-level asset prompt field.

**Architecture:** Keep the change local to the existing settings page. The selected variant determines a preview key from its id and original URL; the visible image uses the selected thumbnail until a hidden original-image loader reports completion for that exact key, then switches to the cached original.

**Tech Stack:** React 19, TypeScript, Ant Design 6, Vitest, Testing Library

---

### Task 1: Stable visual gallery preview

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`
- Test: `frontend/src/pages/projects/production-workbench/settings.test.tsx`

- [x] **Step 1: Write failing interaction tests**

Cover removal of the gallery prompt field, thumbnail-first rendering, original-image promotion after load, and immediate source changes when another thumbnail is selected.

- [x] **Step 2: Run the focused test and verify RED**

Run: `npm test -- src/pages/projects/production-workbench/settings.test.tsx -t "manages visual variants"`

Observed: FAIL because the gallery prompt textbox still existed.

- [x] **Step 3: Implement keyed preview loading**

Track the loaded original using a `${variant.id}:${originalUrl}` key. Render the selected thumbnail while that key is not loaded, preload the original in a hidden keyed image, and promote the original only from that loader's `onLoad` event. Remove the gallery-level `Input.TextArea` while retaining generator prompt controls.

- [x] **Step 4: Run focused and full settings tests**

Run: `npm test -- src/pages/projects/production-workbench/settings.test.tsx`

Observed: 19 tests passed.

- [x] **Step 5: Run static verification**

Run: `npm run tsc`

Observed: PASS with no TypeScript diagnostics.

Run: `npx antd lint ./src`

Observed: completed successfully with 11 pre-existing warnings outside the modified files.

- [x] **Step 6: Commit implementation**

```bash
git add frontend/src/pages/projects/production-workbench/settings.tsx frontend/src/pages/projects/production-workbench/settings.test.tsx
git add -f docs/superpowers/plans/2026-09-18-visual-gallery-preview-loading.md
git commit -m "fix(settings): stabilize gallery image switching"
```
