## 1. Billing Rule Model and Migration

- [ ] 1.1 Add failing migration tests covering model-scoped point price versions, automatic version sequencing, future-version revocation, effective-period overlap, and backward-compatible reading of existing accounting history.
- [x] 1.2 Add Flyway schema changes for model-level point price selectors, version lifecycle status, model-and-price-type version uniqueness, and execution references to frozen cost and point price versions.
- [x] 1.3 Add or update price-version entities, mappers, status types, and response models for querying cost and point price histories by model.
- [ ] 1.4 Migrate or explicitly retire scene-scoped point-policy publication and resolution so new price rules are selected solely by model, metric, dimensions, and effective time.
- [ ] 1.5 Add migration coverage proving that historical usage-cost lines, reservations, and point-ledger entries remain readable without rewriting settled accounting data.

## 2. Price Version Lifecycle Services

- [ ] 2.1 Add failing service tests proving cost and point version numbers are generated independently and monotonically for each model under concurrent publication.
- [ ] 2.2 Implement transactional automatic version-number allocation for each model and price type; remove client-supplied version numbers from publish requests.
- [ ] 2.3 Add failing service tests for valid components, invalid metrics, invalid unit sizes, negative prices, blank cost currency, and overlapping effective intervals.
- [x] 2.4 Implement model cost-price and point-price publication validation, including immutable component persistence and automatic closure of eligible earlier open-ended versions.
- [x] 2.5 Add failing service tests proving only future versions can be revoked and revoked versions never resolve as effective.
- [x] 2.6 Implement authorized future-version revocation and read-only enforcement for effective and historical versions.
- [x] 2.7 Add query services returning a selected model's current, future, historical, and revoked cost-price and point-price versions with component details.

## 3. Preflight Resolution and Dual-Price Accounting

- [x] 3.1 Add failing resolver tests for cost and point price matching by model, required metric, normalized dimensions, and effective timestamp.
- [x] 3.2 Implement a shared model billing resolver that returns complete paired cost and point price components or a typed missing-billing result; it must not fall back to scene-specific or zero-price rules.
- [ ] 3.3 Add failing execution tests proving a missing cost component, missing point component, revoked version, and unsupported metric reject a task before reservation and provider contact.
- [ ] 3.4 Integrate dual-price preflight into AI execution creation and provider dispatch, returning a clear platform-configuration error while leaving no reservation, usage, cost, or provider call record on rejection.
- [x] 3.5 Persist frozen cost-price and point-price version references on the execution version before point reservation; adapt usage costing and settlement to consume the frozen rules.
- [ ] 3.6 Add failing tests proving retries preserve frozen prices and idempotent reservations, while explicit regeneration resolves current effective prices for its new execution version.
- [x] 3.7 Extend platform execution accounting detail to return cost and point version/component evidence alongside usage, cost amount, settled points, and settlement status.

## 4. Platform Billing Management APIs and Authorization

- [x] 4.1 Add controller contract and permission tests for listing model billing histories, publishing cost prices, publishing point prices, and revoking only future versions.
- [x] 4.2 Implement platform APIs that accept a model identifier selected from enabled models, effective dates, and component inputs but never accept a client version number.
- [x] 4.3 Enforce platform billing permissions on all management and accounting-detail endpoints; verify tenant users cannot access supplier cost data.
- [x] 4.4 Regenerate the frontend OpenAPI client after controller contracts stabilize, without manually editing generated files.

## 5. Model Billing Management Frontend

- [x] 5.1 Add route, menu localization, access control, and navigation redirect for a platform-only `AI 服务管理 / 模型计费` page.
- [x] 5.2 Add frontend service types and calls for enabled-model lookup, cost-price history, point-price history, publishing a version, and revoking a future version using regenerated OpenAPI clients where available.
- [x] 5.3 Build a searchable enabled-model dropdown that displays model name, Code, and Provider and disallows free-form model identifiers.
- [x] 5.4 Build separate cost-price and point-price version views showing lifecycle state, auto-assigned version number, effective interval, and metric component table.
- [x] 5.5 Build publish forms without a version-number input; validate metric, positive unit size, non-negative rate, and cost currency before submission.
- [x] 5.6 Add future-version revoke confirmation and ensure effective, expired, and revoked versions render read-only with unavailable revoke controls.
- [ ] 5.7 Add frontend unit tests for model selection, automatic-version presentation, publish validation, permission-gated controls, lifecycle states, and revoke behavior.

## 6. End-to-End Verification and Documentation

- [ ] 6.1 Add integration tests for text, image, and video execution showing complete dual-price resolution, point reservation, settlement, cost snapshots, and accounting-detail evidence.
- [ ] 6.2 Add regression tests showing missing billing rules prevent provider requests and that retry, callback duplication, cancellation, timeout, and regeneration do not create duplicate charges or mutate historical snapshots.
- [x] 6.3 Update the AI operations runbook with model-level price publication, future-version revocation, fail-closed missing-billing handling, and dual-price reconciliation guidance.
- [x] 6.4 Run targeted backend migration, accounting, points, execution, controller, and architecture tests; then run the complete backend suite.
- [ ] 6.5 Run frontend unit tests, type checking, Biome lint, Ant Design lint, and production build; manually smoke-test the model billing page with an authorized platform administrator and a non-platform user.
