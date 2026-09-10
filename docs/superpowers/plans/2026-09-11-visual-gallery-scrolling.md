# Visual Gallery Scrolling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep gallery thumbnails uniformly sized, support wheel-based horizontal browsing, and retain a right-side add entry when thumbnails overflow.

**Architecture:** The thumbnail area becomes a flexible scrolling viewport plus a non-scrolling right-side add-card container. A React ref reads and updates the viewport's `scrollLeft` in a wheel handler; it prevents default page scrolling only when the requested horizontal movement is possible.

**Tech Stack:** React 19, TypeScript, Ant Design 6, Vitest, Testing Library.

---

### Task 1: Add a failing scroll and fixed-entry test

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.test.tsx:360-430`

- [ ] **Step 1: Write the failing assertions**

In the gallery test, find the thumbnail viewport by `aria-label="视觉形象缩略图列表"`, set `scrollWidth` to `600`, `clientWidth` to `180`, and dispatch a wheel event with `deltaY: 80`. Assert that its `scrollLeft` becomes `80` and `preventDefault` was called. Assert `新增变装` is outside this viewport by checking it is found within `视觉形象新增入口`.

- [ ] **Step 2: Verify the test fails**

Run `npm --prefix frontend test -- src/pages/projects/production-workbench/settings.test.tsx`.

Expected: fail because the list has neither a labelled viewport nor a wheel handler and the add card remains in the scrolling list.

### Task 2: Split the scrolling viewport and fixed add entry

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx:420-440,1350-1540`
- Test: `frontend/src/pages/projects/production-workbench/settings.test.tsx:360-430`

- [ ] **Step 1: Add a viewport ref and wheel handler**

Import `WheelEvent` as a type from React. Add `const thumbnailViewportRef = useRef<HTMLDivElement>(null);` beside gallery state. Define `scrollVisualThumbnails(event: WheelEvent<HTMLDivElement>)`: compute `nextScrollLeft = clamp(current + event.deltaY, 0, scrollWidth - clientWidth)`; when `nextScrollLeft !== current`, call `event.preventDefault()` and assign `scrollLeft = nextScrollLeft`.

- [ ] **Step 2: Make each thumbnail fixed-size**

Keep each thumbnail wrapper at `flex: '0 0 92px'` and add `width: 92` and `height: 118`. Make its selection button `height: '100%'` so image and caption cannot cause variable card heights.

- [ ] **Step 3: Create the two-column strip**

Wrap the existing variant map in a `div` with `ref={thumbnailViewportRef}`, `aria-label="视觉形象缩略图列表"`, `flex: 1`, `minWidth: 0`, `overflowX: 'auto'`, and `onWheel={scrollVisualThumbnails}`. Move the `addingVariant ? ... : 新增变装` block into a sibling `div` with `aria-label="视觉形象新增入口"` and `flex: '0 0 92px'`. The sibling stays outside the viewport and therefore remains visible while the thumbnail map scrolls.

- [ ] **Step 4: Verify and commit**

Run `npm --prefix frontend test -- src/pages/projects/production-workbench/settings.test.tsx`, `npm --prefix frontend run tsc`, and `npx --prefix frontend biome check src/pages/projects/production-workbench/settings.tsx src/pages/projects/production-workbench/settings.test.tsx`. Commit only those source and test files with `feat(workbench): improve visual thumbnail scrolling`.
