## 1. Effective Prompt Contract

- [x] 1.1 Add failing backend tests proving primary character, scene, and prop workspace responses expose the canonical asset prompt and ignore stored primary-variant prompts.
- [x] 1.2 Add failing backend tests proving non-primary variants expose their own persisted prompts and empty prompts are not derived during reads or generation preparation.
- [x] 1.3 Implement a single effective-prompt resolver in the visual-variant service and remove runtime prompt concatenation and persistence.
- [x] 1.4 Add failing backend tests proving primary prompt edits update the owning canonical asset while non-primary edits update only the variant.
- [x] 1.5 Implement primary-aware prompt update routing without changing unrelated visual-variant metadata behavior.

## 2. Image Task Consistency

- [x] 2.1 Add failing image-task service tests proving primary tasks use the canonical prompt and non-primary tasks use the variant prompt unchanged.
- [x] 2.2 Add failing tests for actionable rejection when the applicable canonical or variant prompt is empty.
- [x] 2.3 Update image-task preparation to consume the shared effective-prompt resolver and retain canonical character reference-image behavior for non-primary looks.

## 3. Canonical Character Prompt Quality

- [x] 3.1 Add failing Skill contract tests for neutral reusable character prompts and explicit exclusions for episode location, action, transient pose/expression/emotion, held plot props, and camera language.
- [x] 3.2 Update `short-drama-asset-recognition-framework/SKILL.md` with the stable canonical-character boundary and a compliant example.
- [x] 3.3 Add failing asset-persistence tests that reject structurally invalid required or regenerated AI character prompts atomically with actionable correction messages.
- [x] 3.4 Implement deterministic character Markdown structure validation at the episode asset save boundary without applying heuristic keyword deletion or changing manual historical prompts.

## 4. Production Workbench

- [x] 4.1 Add failing settings-page tests proving the primary generation modal shows and saves the canonical prompt while a non-primary modal shows and saves its variant prompt.
- [x] 4.2 Update the visual workspace and prompt-save flow so the displayed prompt is the effective persisted prompt returned by the server, with no client-side derivation.
- [x] 4.3 Add UI coverage for an empty applicable prompt and verify generation remains blocked with an actionable message.

## 5. Verification And Release

- [x] 5.1 Run focused backend tests for asset persistence, visual variants, image tasks, and Skill contracts.
- [x] 5.2 Run focused frontend production-workbench settings tests.
- [x] 5.3 Run backend and frontend type/lint checks required by the repository, including `npm run lint` and `npx antd lint ./src` for affected frontend code.
- [x] 5.4 Document coordinated deployment of backend code and the updated file-backed Skill, confirming no database migration or historical prompt rewrite is required.
