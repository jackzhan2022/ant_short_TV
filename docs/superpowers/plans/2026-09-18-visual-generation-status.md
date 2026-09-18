# Visual Generation Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show generation progress and state inside the image-rendering areas of asset cards, the gallery main preview, and gallery thumbnails.

**Architecture:** Add one local overlay component that renders a spinner for `GENERATING`, text feedback for incomplete or failed states, and nothing for `COMPLETED`. Reuse it in all three image areas and derive the outer card's prioritized aggregate state from `generationSummary` without changing APIs or introducing polling.

**Tech Stack:** React 19, TypeScript, Ant Design 6, Vitest, Testing Library

---

### Task 1: Three-level generation status overlays

**Files:**
- Modify: `frontend/src/pages/projects/production-workbench/settings.tsx`
- Test: `frontend/src/pages/projects/production-workbench/settings.test.tsx`

- [x] **Step 1: Write failing overlay assertions**

Set the fixture summary to include `GENERATING: 1`. Assert the outer card and generating thumbnail contain a progress indicator, completed previews have no overlay, selecting the generating variant adds a main-preview progress indicator, and failed variants render failure text inside their image area.

```tsx
expect(within(screen.getByLabelText('斌斌视觉形象生成状态')).getByRole('progressbar')).toBeInTheDocument();
expect(screen.queryByLabelText('日常形象主预览生成状态')).not.toBeInTheDocument();
expect(within(screen.getByLabelText('婚礼礼服缩略图生成状态')).getByRole('progressbar')).toBeInTheDocument();
```

- [x] **Step 2: Run the focused test and verify RED**

Run: `npm test -- src/pages/projects/production-workbench/settings.test.tsx -t "manages visual variants"`

Expected: FAIL because existing badges do not contain progress indicators and completed statuses are still rendered.

- [x] **Step 3: Implement the shared image overlay**

Create an absolutely positioned overlay with compact mode for thumbnails. Render `Spin` and “生成中” for active work, “等待生成” for `NOT_STARTED`, red “生成失败” for `FAILED`, raw text for unknown states, and `null` for `COMPLETED`. Place it inside each image container and remove external status tags.

```tsx
const GenerationStatusOverlay = ({ status, label, compact = false }) =>
  status === 'COMPLETED' ? null : (
    <span aria-label={label}>
      {status === 'GENERATING' ? <Spin size={compact ? 'small' : 'default'} /> : null}
      {generationStatusLabel(status)}
    </span>
  );
```

- [x] **Step 4: Verify the affected batch**

Run: `npm test -- src/pages/projects/production-workbench/settings.test.tsx`

Expected: all settings tests PASS.

Run: `npm run tsc`

Run: `npm run biome:lint`

Expected: type-check and lint complete with no new errors.

- [x] **Step 5: Commit**

```bash
git add frontend/src/pages/projects/production-workbench/settings.tsx frontend/src/pages/projects/production-workbench/settings.test.tsx
git add -f docs/superpowers/plans/2026-09-18-visual-generation-status.md
git commit -m "fix(settings): render generation loading overlays"
```
