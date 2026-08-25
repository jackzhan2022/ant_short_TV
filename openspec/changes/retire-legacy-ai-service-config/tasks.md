## 1. Lock the Target Architecture with Tests

- [x] 1.1 Add backend architecture tests that fail while production code depends on `AiServiceConfig*`, `serviceConfigId`, `legacyServiceConfigId`, legacy credential fallback, or direct provider HTTP outside registered adapters.
- [x] 1.2 Add backend request and response contract tests requiring `modelId` for provider-backed image and video workflows and no configuration identifier for the local voice placeholder.
- [x] 1.3 Add routing tests for explicit Model selection, project/default fallback, capability mismatch, disabled Model, disabled Provider, missing Provider credentials, and the rule that an invalid explicit Model never falls back.
- [x] 1.4 Add frontend access and route tests proving platform configuration pages require platform permissions and tenant call-log access grants no Provider or Model management access.

## 2. Make Platform Routing the Only Configuration Core

- [x] 2.1 Refactor `AiModelRouter` to resolve only `ai_model`, enabled Model Capabilities, `ai_provider`, and `ai_provider_config`, then remove `AiServiceConfigMapper` and `legacyConfig()`.
- [x] 2.2 Refactor `ProjectAiConfigService` to validate selected and default Models exclusively through platform Provider, Model, and Capability state.
- [x] 2.3 Remove `syncLegacyConfig()` from `PlatformAiManagementService` and ensure platform Provider/Model create, update, enable, default, and connectivity-test operations remain the sole write path.
- [x] 2.4 Update canonical routing error tests so missing credentials and disabled or incompatible configuration fail before provider contact and create no provider-success accounting evidence.

## 3. Convert Image Workflows to Model Identity

- [x] 3.1 Replace image `serviceConfigId` request, response, entity, mapper, and task-hash fields with `modelId`, preserving explicit request, project selection, and platform default resolution order.
- [x] 3.2 Remove image legacy service lookup and legacy-to-model translation, and persist the requested and resolved Model on the image domain task and shared execution.
- [x] 3.3 Ensure the image execution handler reaches Provider transport only through `AiInvocationService` with the resolved Model and retains existing result, usage, cost, and point-settlement behavior.
- [x] 3.4 Update image controller, service, execution, retry/regeneration, and persistence tests for valid Model selection and fail-closed invalid configuration.

## 4. Convert Video Workflows to Model Identity and Unified Invocation

- [x] 4.1 Replace video `serviceConfigId` request, response, entity, mapper, idempotency-hash, retry, regeneration, and polling fields with `modelId`.
- [x] 4.2 Resolve video Models through explicit request, project selection, or platform default and persist the requested and resolved Model on the domain task and shared execution.
- [x] 4.3 Move video submission and provider polling behind registered execution handling and `AiInvocationService`, removing direct reads of legacy credentials, direct provider HTTP configuration, and legacy model lookup.
- [x] 4.4 Update video controller, execution, adapter, polling, retry, cancellation, and result tests to cover synchronous and provider-native asynchronous outcomes using platform Model routes.

## 5. Remove Configuration from the Local Voice Placeholder

- [x] 5.1 Remove `serviceConfigId` and `AiServiceConfigMapper` from voice request, response, entity, mapper, copy/regeneration, and local placeholder generation paths without adding `modelId`.
- [x] 5.2 Add voice tests proving the local placeholder completes without Provider routing, AI invocation logs, usage cost, or point settlement.

## 6. Remove Legacy Backend APIs, Logs, and Tenant Configuration Permissions

- [x] 6.1 Remove `AiServiceConfigController`, `AiServiceConfigService`, Mapper, Entity, request/response/status/test types, test-log persistence types, and their dedicated tests.
- [x] 6.2 Remove `service_config_id` writes, joins, response fields, and entity properties from unified and video call-log code while retaining Model, Provider, execution, attempt, tenant, and outcome metadata.
- [x] 6.3 Introduce `AI_CALL_LOG:VIEW`, apply it to tenant-scoped call-log APIs, retain `AI_SERVICE:USE`, and remove `AI_SERVICE:VIEW/CREATE/EDIT/DELETE/TEST` definitions and role assignments.
- [x] 6.4 Update RBAC, controller authorization, tenant isolation, secret masking, and call-log query tests for the separated usage, log-view, and platform-configuration permissions.

## 7. Remove the Legacy Frontend Feature and Update Contracts

- [x] 7.1 Delete the handwritten Service Config page, data/service modules, page tests, route, menu locale entries, and `canView/Create/Edit/Delete/TestAiServices` access flags.
- [x] 7.2 Update AI management navigation so platform-authorized users enter Providers while tenant users with `AI_CALL_LOG:VIEW` can reach only tenant-scoped logs.
- [x] 7.3 Replace `serviceConfigId` with `modelId` in provider-backed production-workbench types, forms, payloads, responses, fixtures, and tests; remove configuration selection from the local voice placeholder UI.
- [x] 7.4 Regenerate `frontend/src/services/ant-design-pro/` with `npm run openapi` after backend contracts compile, and verify the generated client contains no AI Service Config controller or legacy identifier types.

## 8. Apply the Destructive Schema Cleanup

- [x] 8.1 Add the next Flyway migration to clear project and nullable historical references to legacy-derived Models, delete their dependent capabilities and pricing records, and delete Models whose `legacy_service_config_id` is non-null without deleting unrelated business data.
- [x] 8.2 In the migration, clear Provider credential ciphertext and connectivity state, disable Provider configurations, and require explicit platform reconfiguration after startup.
- [x] 8.3 Add missing `model_id` columns for provider-backed task tables, remove legacy `service_config_id` columns and indexes from task/call-log tables, and remove `legacy_service_config_id` from `ai_model`.
- [x] 8.4 Drop `ai_service_test_log` and `ai_service_config`, and remove legacy configuration permission and role-assignment rows while retaining `AI_SERVICE:USE` and adding `AI_CALL_LOG:VIEW` assignments.
- [x] 8.5 Add a migration rehearsal test with representative legacy configurations, derived Models, project selections, tasks, logs, pricing references, and unrelated business records; verify cleanup, credential reset, referential integrity, and preservation of unrelated data.

## 9. Documentation and Final Verification

- [x] 9.1 Update the AI invocation contract, workflow inventory, operations runbook, and migration verification guide to describe platform-only configuration, model-based routing, local voice behavior, and the required Provider reconfiguration step.
- [x] 9.2 Run targeted backend tests for routing, platform management, image, video, voice, logs, RBAC, execution, accounting, and migration rehearsal, then run the complete backend test suite.
- [x] 9.3 Run frontend unit tests, `npm run tsc`, `npm run lint`, `npx antd lint ./src`, and `npm run build` from `frontend`.
- [x] 9.4 Run repository and schema scans proving production code and final schema contain no `AiServiceConfig`, `ai_service_config`, `serviceConfigId`, `service_config_id`, `legacyServiceConfigId`, or `legacy_service_config_id`, excluding immutable historical migrations and archived documentation where explicitly allowed.
- [ ] 9.5 Start the application against the migrated development database, reconfigure one Provider and compatible Models through platform APIs/UI, and smoke-test permitted image and video execution plus local voice placeholder and tenant-scoped call-log access.
