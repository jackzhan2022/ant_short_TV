# Stable Image Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent partial image painting by revealing asset images only after their current URL has loaded and decoded.

**Architecture:** Create a focused `StableImage` component with URL-keyed ready and failure state plus optional low-resolution preview support. Replace the three direct image render paths in the settings page while leaving server generation overlays independent.

**Tech Stack:** React 19, TypeScript, Ant Design 6, Vitest, Testing Library

---

### Task 1: StableImage component

**Files:**
- Create: `frontend/src/pages/projects/production-workbench/StableImage.tsx`
- Create: `frontend/src/pages/projects/production-workbench/StableImage.test.tsx`

- [x] **Step 1: Write failing component tests**

Test that the target image starts transparent, becomes visible only after `load` and `decode`, resets when `src` changes, keeps a decoded preview visible until the target is ready, and shows the fallback for missing or failed URLs.

```tsx
const decode = vi.fn(() => Promise.resolve());
Object.defineProperty(HTMLImageElement.prototype, 'decode', { configurable: true, value: decode });

const image = screen.getByAltText('角色图');
expect(image).toHaveStyle({ opacity: '0' });
fireEvent.load(image);
await waitFor(() => expect(image).toHaveStyle({ opacity: '1' }));
```

- [x] **Step 2: Verify RED**

Run: `npm test -- src/pages/projects/production-workbench/StableImage.test.tsx`

Expected: FAIL because `StableImage` does not exist.

- [x] **Step 3: Implement the minimal component**

Render preview and target layers at opacity zero until their exact URL is decoded. Compare ready and failed state to the current URL during render so stale asynchronous completions cannot reveal the wrong image. Render a centered `Spin` while neither layer is ready.

- [x] **Step 4: Verify component GREEN**

Run: `npm test -- src/pages/projects/production-workbench/StableImage.test.tsx`

Expected: all component tests PASS.

### Task 2: Integrate all settings image paths

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/settings.test.tsx`

- [x] **Step 1: Write failing integration assertions**

Assert the asset-card thumbnail is transparent before load and visible after load. Update the gallery test to assert the decoded preview appears first and the decoded original replaces it after its own load.

- [x] **Step 2: Verify integration RED**

Run: `npm test -- src/pages/projects/production-workbench/settings.test.tsx -t "stable image|manages visual variants"`

Expected: FAIL because the asset card and thumbnails still use direct images and the main preview still uses local preload state.

- [x] **Step 3: Replace the three render paths**

Use `StableImage` for `AssetCard`, each gallery thumbnail, and the selected gallery preview. Remove `loadedVisualPreviewKey` and the hidden manual preloader from `settings.tsx`.

- [x] **Step 4: Verify the affected batch**

Run: `npm test -- src/pages/projects/production-workbench/StableImage.test.tsx src/pages/projects/production-workbench/settings.test.tsx`

Run: `npm run tsc`

Run: `npm run biome:lint`

Expected: component and settings tests pass; type-check and lint have no new errors.

- [x] **Step 5: Commit**

```bash
git add frontend/src/pages/projects/production-workbench/StableImage.tsx frontend/src/pages/projects/production-workbench/StableImage.test.tsx frontend/src/pages/projects/production-workbench/settings.tsx frontend/src/pages/projects/production-workbench/settings.test.tsx
git add -f docs/superpowers/plans/2026-09-18-stable-image-rendering.md
git commit -m "fix(settings): reveal images after decode"
```
