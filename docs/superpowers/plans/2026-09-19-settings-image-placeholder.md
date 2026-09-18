# Settings Image Placeholder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace name-initial fallbacks with one consistent no-image placeholder across the project settings page.

**Architecture:** Add a focused `AssetImagePlaceholder` component with standard and compact presentations. Pass it to the existing `StableImage` fallback prop in the asset card, gallery thumbnail, and gallery main preview paths.

**Tech Stack:** React 19, TypeScript, Ant Design 6, Vitest, Testing Library

---

### Task 1: Shared placeholder component

**Files:**
- Create: `frontend/src/pages/projects/production-workbench/AssetImagePlaceholder.tsx`
- Create: `frontend/src/pages/projects/production-workbench/AssetImagePlaceholder.test.tsx`

- [x] **Step 1: Write failing component tests**

Assert the standard mode exposes a picture icon and visible “暂无图片” text, while compact mode exposes the same semantic label without visible text.

```tsx
render(<AssetImagePlaceholder />);
expect(screen.getByRole('img', { name: '暂无图片' })).toHaveTextContent('暂无图片');

render(<AssetImagePlaceholder compact />);
expect(screen.getByRole('img', { name: '暂无图片' })).not.toHaveTextContent('暂无图片');
```

- [x] **Step 2: Verify RED**

Run: `npm test -- src/pages/projects/production-workbench/AssetImagePlaceholder.test.tsx`

Expected: FAIL because the component does not exist.

- [x] **Step 3: Implement the component**

Use `PictureOutlined`, existing app color variables, and responsive parent-sized layout. Render text only in standard mode and keep `aria-label="暂无图片"` in both modes.

- [x] **Step 4: Verify component GREEN**

Run: `npm test -- src/pages/projects/production-workbench/AssetImagePlaceholder.test.tsx`

Expected: both component tests PASS.

### Task 2: Replace settings fallbacks

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`
- Modify: `frontend/src/pages/projects/production-workbench/settings.test.tsx`

- [x] **Step 1: Write failing integration assertions**

Remove image URLs from the fixture and assert standard placeholders appear for the asset card and main preview, the thumbnail uses compact mode, and no asset-name initial remains in those image regions.

- [x] **Step 2: Verify integration RED**

Run: `npm test -- src/pages/projects/production-workbench/settings.test.tsx -t "unified image placeholders"`

Expected: FAIL because current fallbacks still render name initials.

- [x] **Step 3: Integrate the placeholder**

Pass `<AssetImagePlaceholder />` to asset cards and main previews, and `<AssetImagePlaceholder compact />` to gallery thumbnails. Keep `StableImage` and generation overlays unchanged.

- [x] **Step 4: Verify the affected batch**

Run: `npm test -- src/pages/projects/production-workbench/AssetImagePlaceholder.test.tsx src/pages/projects/production-workbench/StableImage.test.tsx src/pages/projects/production-workbench/settings.test.tsx`

Run: `npm run tsc`

Run: `npm run biome:lint`

Expected: related tests pass; type-check and lint have no new errors.

- [x] **Step 5: Commit**

```bash
git add frontend/src/pages/projects/production-workbench/AssetImagePlaceholder.tsx frontend/src/pages/projects/production-workbench/AssetImagePlaceholder.test.tsx frontend/src/pages/projects/production-workbench/settings.tsx frontend/src/pages/projects/production-workbench/settings.test.tsx
git add -f docs/superpowers/plans/2026-09-19-settings-image-placeholder.md
git commit -m "feat(settings): unify empty image placeholders"
```
