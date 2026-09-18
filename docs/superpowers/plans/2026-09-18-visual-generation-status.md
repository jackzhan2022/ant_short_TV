# Visual Generation Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show consistent Chinese visual-generation status badges on asset cards, the gallery main preview, and gallery thumbnails.

**Architecture:** Add one local status-to-presentation mapper in the settings page and derive one prioritized aggregate status from each asset's `generationSummary`. Reuse the mapper for the aggregate badge and each variant badge without changing APIs or introducing polling.

**Tech Stack:** React 19, TypeScript, Ant Design 6, Vitest, Testing Library

---

### Task 1: Three-level generation status badges

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`
- Test: `frontend/src/pages/projects/production-workbench/settings.test.tsx`

- [x] **Step 1: Write failing status assertions**

Set the fixture summary to include `GENERATING: 1`, then assert the outer card, main preview, and both thumbnails expose Chinese status labels through distinct accessible names.

```tsx
expect(screen.getByLabelText('斌斌视觉形象生成状态')).toHaveTextContent('生成中 1');
expect(screen.getByLabelText('日常形象主预览生成状态')).toHaveTextContent('已完成');
expect(screen.getByLabelText('日常形象缩略图生成状态')).toHaveTextContent('已完成');
expect(screen.getByLabelText('婚礼礼服缩略图生成状态')).toHaveTextContent('生成失败');
```

- [x] **Step 2: Run the focused test and verify RED**

Run: `npm test -- src/pages/projects/production-workbench/settings.test.tsx -t "manages visual variants"`

Expected: FAIL because the accessible Chinese status badges do not exist.

- [x] **Step 3: Implement the shared mapping and badges**

Add a mapper for `NOT_STARTED`, `GENERATING`, `COMPLETED`, and `FAILED`, preserving unknown backend values. Derive the asset aggregate using priority `GENERATING`, `FAILED`, `NOT_STARTED`, `COMPLETED`. Replace the old pending-count overlay, translate the main status tag, and add a non-overlapping badge to each thumbnail.

```tsx
const generationStatusPresentation = (status: string) => {
  const presentations: Record<string, { label: string; color?: string }> = {
    NOT_STARTED: { label: '待生成' },
    GENERATING: { label: '生成中', color: 'blue' },
    COMPLETED: { label: '已完成', color: 'green' },
    FAILED: { label: '生成失败', color: 'red' },
  };
  return presentations[status] ?? { label: status };
};
```

- [x] **Step 4: Verify the affected batch**

Run: `npm test -- src/pages/projects/production-workbench/settings.test.tsx`

Expected: all settings tests PASS.

Run: `npm run tsc && npm run biome:lint`

Expected: type-check and lint complete with no new errors.

- [x] **Step 5: Commit**

```bash
git add frontend/src/pages/projects/production-workbench/settings.tsx frontend/src/pages/projects/production-workbench/settings.test.tsx
git add -f docs/superpowers/plans/2026-09-18-visual-generation-status.md
git commit -m "feat(settings): show visual generation statuses"
```
