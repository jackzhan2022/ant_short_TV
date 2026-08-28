## 1. Extract Reusable Content

- [x] 1.1 Extract member management content into an exportable component while preserving owner-only actions and selected-team loading.
- [x] 1.2 Extract role management and permission tree content into exportable components without changing API behavior.

## 2. Unified Navigation

- [x] 2.1 Add four tabs to `/team/my`, wiring each tab to the corresponding reusable content and defaulting to Team List.
- [x] 2.2 Hide legacy member and role menu entries and redirect their routes to `/team/my`.

## 3. Verification

- [x] 3.1 Update or add route and page tests covering four tabs, legacy redirects, and owner-only controls.
- [x] 3.2 Run focused frontend tests, lint, and production build.
