# Visual Gallery Responsive Height Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the visual gallery within the viewport while preserving a useful image preview area.

**Architecture:** Apply viewport-based maximum height to the gallery modal body and replace the fixed preview height with a CSS `clamp` expression. No service or state changes are needed.

**Tech Stack:** React, TypeScript, Ant Design, Vitest.

---

### Task 1: Cover and implement responsive gallery height

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.test.tsx:360-430`
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx:1333-1732`

- [ ] **Step 1: Write the failing assertion**

In the gallery test, locate the preview image container via a new `data-testid="视觉形象主图预览"` and assert its style height is `clamp(260px, 48vh, 430px)`.

- [ ] **Step 2: Verify failure**

Run `npm --prefix frontend test -- src/pages/projects/production-workbench/settings.test.tsx`.

Expected: fail because the preview uses a fixed `430px` height.

- [ ] **Step 3: Implement responsive sizing**

Set the gallery Modal `styles.body` to `{ maxHeight: '88vh', overflowY: 'auto' }`. Add the test id to the preview container and replace `height: 430` with `height: 'clamp(260px, 48vh, 430px)'`.

- [ ] **Step 4: Verify and commit**

Run the focused settings test, `npm --prefix frontend run tsc`, and Biome check on both files. Commit them with `feat(workbench): adapt visual gallery height`.
