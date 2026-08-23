# Real Backend Only Design

## Goal

Ensure the frontend never serves runtime Mock API responses and always uses the configured real backend API.

## Scope

- Remove global and page-level runtime Mock handlers from `frontend`.
- Make every development start command set `MOCK=none`.
- Remove the Umi runtime Mock configuration and the unused `mockjs` production-facing declaration/dependency.
- Update frontend documentation so it no longer instructs developers to use runtime Mock APIs.
- Keep Vitest `vi.mock`, `tests/__mocks__`, and browser/test environment doubles because they do not intercept runtime HTTP requests.

## Data Flow

Browser requests under `/api/` go through the frontend development proxy in `frontend/config/proxy.ts` and are forwarded to `API_PROXY_TARGET`, defaulting to `http://localhost:8080`. No frontend Mock handler may register an `/api` route.

## Verification

- Static checks confirm runtime Mock directories and handlers are absent.
- Frontend tests and TypeScript checks pass.
- A real request through `http://localhost:8000/api/auth/login` reaches the backend and returns the backend response rather than a frontend Mock response.
