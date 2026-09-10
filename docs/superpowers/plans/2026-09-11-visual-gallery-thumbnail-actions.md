# Visual Gallery Thumbnail Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move visual-variant creation and deletion into the gallery thumbnail row, with a confirmation guard before deletion.

**Architecture:** Retain the existing visual-variant service calls and gallery state in `settings.tsx`. Replace the separate creation controls with a final thumbnail-card entry, wrap each existing thumbnail in a hoverable positioned container, and use Ant Design's confirmation control before calling the existing delete mutation.

**Tech Stack:** React 19, TypeScript, Ant Design 6, Vitest, Testing Library.

---

### Task 1: Cover card creation and guarded deletion

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.test.tsx:1-470`

- [ ] **Step 1: Write the failing test**

Extend the Ant Design mock with `Popconfirm`, then update the gallery test to click `新增变装`, enter `雨夜造型` in `新视觉形象名称`, and click `确认新增视觉形象`. Assert that the old preview `删除` button is absent. Hover `视觉形象缩略图-婚礼礼服`, click `删除婚礼礼服`, assert the delete service has not run, then click `确认删除婚礼礼服` and assert it has run.

- [ ] **Step 2: Verify it fails**

Run `npm --prefix frontend test -- src/pages/projects/production-workbench/settings.test.tsx`.

Expected: fail because neither the final add card nor thumbnail delete confirmation exists.

### Task 2: Render the thumbnail-row operations

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx:420-590,1340-1445,1568-1601`
- Test: `frontend/src/pages/projects/production-workbench/settings.test.tsx:360-405`

- [ ] **Step 1: Add the expanded-card state**

Add `const [addingVariant, setAddingVariant] = useState(false);` beside `newVariantName`. On successful `addVariant`, clear the name, close the card, and reload.

- [ ] **Step 2: Replace the left-side form with the final add card**

Delete the flex block containing the persistent input and `新增` button. After `variants.map`, render a fixed-width dashed button with `aria-label="新增变装"`; clicking it expands an inline card containing the existing labelled input and a primary button labelled `确认新增视觉形象`.

- [ ] **Step 3: Add the hover delete icon**

Wrap each existing thumbnail select button in a positioned `div` with `data-testid={`视觉形象缩略图-${variant.name}`}`. Add a `Popconfirm`-wrapped `DeleteOutlined` icon button in its top-right corner. It is hidden until the wrapper is hovered, calls `event.stopPropagation()`, and confirms deletion through the existing `mutateVariant(() => deleteVisualVariant(...))` path.

- [ ] **Step 4: Remove the duplicate preview action**

Remove the preview metadata area's text `删除` button. The thumbnail's confirmed icon action is the sole deletion entry.

- [ ] **Step 5: Verify and commit**

Run `npm --prefix frontend test -- src/pages/projects/production-workbench/settings.test.tsx`, `npm --prefix frontend run tsc`, and `git diff --check`. Commit only `settings.tsx` and `settings.test.tsx` using `feat(workbench): move visual actions to thumbnails`.
