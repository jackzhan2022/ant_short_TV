## 1. Database and catalog model

- [x] 1.1 Add migration contract tests for the entitlement definition table, indexes, protected system fields, and idempotent seeds for the three existing system entitlement codes.
- [x] 1.2 Add the Flyway migration plus entitlement definition entity and mapper, preserving existing `commercial_entitlement` rows and text snapshot columns.

## 2. Entitlement catalog backend

- [x] 2.1 Add service tests for ordered catalog queries, generated immutable codes, trimmed and unique display names, display entitlement create/edit/enable/disable, and rejection of system entitlement mutations.
- [x] 2.2 Implement the entitlement catalog service, request/response models, validation, and stable server-generated display entitlement codes.
- [x] 2.3 Add controller permission tests for catalog reads and writes, then expose platform catalog query, create, update, enable, and disable endpoints using the existing commercial permissions.

## 3. Package version integration and runtime safety

- [x] 3.1 Extend package service tests to cover catalog-backed system values, valueless display entitlements, unknown/inactive/duplicate entitlement rejection, name snapshots, publish-time revalidation, and legacy system-name fallback.
- [x] 3.2 Extend package draft and response contracts and implement catalog validation plus `text_value` snapshot persistence without changing generated service files directly.
- [x] 3.3 Extend catalog, order, and subscription snapshot tests to prove entitlement names remain stable after catalog rename or disable.
- [x] 3.4 Add fulfillment, periodic grant, discount resolver, and model execution regression tests proving display entitlements never create grants or affect authorization, routing, or billing.

## 4. Frontend service and tab shell

- [x] 4.1 Run `npx antd info` for Tabs and every Ant Design component newly introduced or materially changed, and record any API adjustments required for antd v6.
- [x] 4.2 Add frontend service tests and types for catalog query, create, update, enable, and disable operations plus the extended package entitlement response.
- [x] 4.3 Refactor套餐管理为一个 `PageContainer` 下的受控“套餐列表/权益管理”Tabs，并添加页面测试验证默认 Tab、切换行为和原套餐功能保留。

## 5. Entitlement management experience

- [x] 5.1 Add failing UI tests for catalog columns, system read-only presentation, permission-gated display entitlement actions, validation, and create/edit/enable/disable refresh behavior.
- [x] 5.2 Implement the entitlement management table and display entitlement form, including category/status labels, immutable code display, confirmations, success feedback, and permission handling.

## 6. Package form and customer display

- [x] 6.1 Add package form tests proving only active catalog entries are selectable, system entries require numeric values, display entries do not render value inputs, and submitted payloads preserve the distinction.
- [x] 6.2 Replace hard-coded package entitlement options with catalog-backed conditional fields while preserving existing draft, version history, publish, and unpublish behavior.
- [x] 6.3 Add purchase page tests for display entitlement snapshot text and legacy system formatting, then render display entitlements without numeric suffixes or runtime claims.

## 7. Verification

- [x] 7.1 Run focused backend schema, catalog, package lifecycle, fulfillment, discount, and permission tests and resolve all failures.
- [x] 7.2 Run focused frontend package management, service, purchase modal, and field dictionary tests and resolve all failures.
- [x] 7.3 Run frontend type checking, Biome lint, `npx antd lint ./src`, and the production build; review the final diff to confirm generated services and unrelated user changes were not modified.
