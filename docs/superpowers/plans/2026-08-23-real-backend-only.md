# Real Backend Only Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove runtime frontend Mock APIs and make all development API calls use the real backend.

**Architecture:** Keep the existing `/api/` development proxy as the only local API boundary. Remove Umi Mock route registration and force all dev scripts to start with `MOCK=none`; preserve test-only mocks.

**Tech Stack:** Umi Max, TypeScript, npm, Vitest, PowerShell.

---

### Task 1: Add the runtime-Mock guard test

**Files:**
- Create: `frontend/config/runtime-backend-only.test.ts`

- [ ] **Step 1: Write the failing test**

Add a test that reads `frontend/package.json` and `frontend/config/config.ts`, asserting every development start script contains `MOCK=none` and the Umi config does not define a runtime `mock` block.

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm --prefix frontend exec vitest run config/runtime-backend-only.test.ts`

Expected: FAIL because `start` currently omits `MOCK=none` and `config.ts` still defines `mock`.

### Task 2: Remove runtime Mock handlers

**Files:**
- Delete: `frontend/mock/user.ts`
- Delete: `frontend/mock/utils.ts`
- Delete: `frontend/src/pages/user/register/_mock.ts`
- Delete: `frontend/types/cache/mock/login.mock.cache.js`

- [ ] **Step 1: Delete the runtime Mock files**

Remove only runtime route handlers and generated runtime Mock cache files. Do not delete `frontend/tests/__mocks__` or test-local `vi.mock` calls.

- [ ] **Step 2: Verify no runtime route handler remains**

Run: `rg -n "POST /api|GET /api|mockUser|mockTenant" frontend/mock frontend/src/pages --glob '!**/*.test.*'`

Expected: no matches, with missing-directory errors for deleted runtime Mock directories acceptable.

### Task 3: Enforce real backend startup

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/config/config.ts`
- Modify: `frontend/src/typings.d.ts`
- Modify: `frontend/docs/cheatsheet.zh-CN.md`
- Modify: `frontend/docs/cheatsheet.en-US.md`

- [ ] **Step 1: Set `MOCK=none` on all development start scripts**

Set `start` to `cross-env UMI_ENV=dev MOCK=none max dev`; retain the same explicit setting on `dev`, `start:no-mock`, `start:pre`, and `start:test`.

- [ ] **Step 2: Remove Umi runtime Mock configuration**

Delete the `mock` block from `frontend/config/config.ts`, including its `include` and `exclude` arrays.

- [ ] **Step 3: Remove the unused `mockjs` declaration and dependency**

Delete `declare module 'mockjs';` from `frontend/src/typings.d.ts`, remove `mockjs` from `frontend/package.json`, and regenerate `frontend/package-lock.json` with `npm --prefix frontend install --package-lock-only`.

- [ ] **Step 4: Update developer documentation**

Replace the Mock startup and runtime Mock sections in both cheatsheets with the real-backend startup command and proxy requirement. Do not remove test mocking guidance.

### Task 4: Make the guard test pass and verify

**Files:**
- Modify: `frontend/config/runtime-backend-only.test.ts`

- [ ] **Step 1: Run the guard test**

Run: `npm --prefix frontend exec vitest run config/runtime-backend-only.test.ts`

Expected: PASS.

- [ ] **Step 2: Run frontend checks**

Run: `npm run frontend:lint`

Expected: TypeScript and Biome checks pass.

- [ ] **Step 3: Start the frontend with the normal command**

Run: `npm --prefix frontend run start` with the backend available on port `8080`.

Expected: the frontend starts with `MOCK=none` and `/api/` requests are proxied to `http://localhost:8080`.

- [ ] **Step 4: Verify the login request through port 8000**

POST `http://localhost:8000/api/auth/login` with a valid backend account and confirm the response contains the backend session payload, not `mock-access-token` or the mock tenant.
