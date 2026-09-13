## 1. Lightweight Asset Workspace API

- [x] 1.1 Add a project-authorized asset settings workspace response that contains only project assets and their existing visual data.
- [x] 1.2 Expose `GET /api/projects/{projectId}/asset-settings-workspace` without changing the complete script workspace contract.
- [x] 1.3 Add controller and service coverage for authorized, unauthorized, empty-asset, and omitted non-asset response fields.

## 2. Asset Settings Loading Experience

- [x] 2.1 Add the typed frontend service for the lightweight asset settings workspace endpoint.
- [x] 2.2 Change initial and post-mutation settings-page refreshes to use the lightweight workspace request with pending candidates.
- [x] 2.3 Render a layout-matched skeleton during initial loading and a retryable failure state when initial data cannot be loaded.
- [x] 2.4 Add frontend tests for loading, success, failure/retry, and lightweight endpoint usage.

## 3. Verification

- [x] 3.1 Run focused backend and frontend tests for the new API and settings page.
- [x] 3.2 Run the applicable frontend type/lint checks and confirm the existing script workspace callers remain unchanged.
