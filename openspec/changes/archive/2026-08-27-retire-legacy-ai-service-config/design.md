## Context

The codebase has a platform AI gateway built around `ai_provider_config`, `ai_model`, `ai_model_capability`, `ProjectAiConfigService`, `AiModelRouter`, and `AiInvocationService`. It also retains the earlier `ai_service_config` stack. Legacy writes synchronise one way into platform records, while several business services still read legacy rows or carry `serviceConfigId`, creating two authorities for credentials, model availability, defaults, and deletion.

The product has not launched. Existing legacy configuration and test data has no preservation requirement, but unrelated project, material, and business data must not be deleted. Flyway migrations V4-V47 remain immutable, so cleanup is delivered through a new forward migration. Platform administrators are the only credential and model owners; tenant users may use permitted AI features and inspect tenant-scoped execution data but cannot supply credentials or define models.

The current shot voice workflow writes a deterministic local placeholder and does not contact an AI provider. It must not create a misleading model dependency or AI accounting record until a real audio adapter and execution handler exist.

## Goals / Non-Goals

**Goals:**

- Establish platform Provider/Model configuration as the only runtime AI configuration authority.
- Remove the legacy UI, API, permissions, Java types, database tables, compatibility columns, and routing fallbacks in one change.
- Make every provider-backed workflow select a platform `modelId` and enter provider transport through `AiModelRouter` and `AiInvocationService`.
- Preserve project and business records where practical while intentionally discarding legacy configuration, legacy-derived models, test logs, and credentials of ambiguous origin.
- Fail before provider contact when no enabled Provider configuration, Model, or required Model Capability is available.

**Non-Goals:**

- Tenant-managed credentials or bring-your-own-key support.
- Migrating or preserving legacy AI service configuration values.
- Implementing a real TTS provider for the local voice placeholder.
- Reworking pricing, point settlement, execution lifecycle, or Agent editing beyond removing legacy configuration dependencies.
- Editing historical Flyway migrations or generated files under `frontend/src/services/ant-design-pro` by hand.

## Decisions

### 1. Platform Provider and Model records are the sole source of truth

`ai_provider_config` owns encrypted credentials, base URL, extra Provider settings, status, and connectivity-test state. `ai_model` and `ai_model_capability` own model identity, service type, availability, defaults, ordering, and supported capabilities. `project_ai_config` may select enabled platform Models but never stores credentials.

Tenant configuration CRUD is removed. `AI_SERVICE:USE` remains a tenant permission for consuming AI features. Configuration permissions are exclusively the existing `PLATFORM_AI_PROVIDER_*` and `PLATFORM_AI_MODEL_*` permissions. Tenant call-log visibility moves from the overloaded `AI_SERVICE:VIEW` permission to a dedicated `AI_CALL_LOG:VIEW` permission so log access does not imply configuration access.

Alternative considered: retain `ai_service_config` as a facade over platform records. Rejected because it preserves two APIs and incompatible tenant/platform ownership semantics.

### 2. Provider-backed contracts use model identity, not configuration identity

Image and video create/regenerate contracts carry optional `modelId`. Resolution order is the explicit request Model, the project's configured Model for the required service type, then the enabled platform default Model. An explicit invalid, disabled, or capability-incompatible Model fails; it never silently falls back. The resolved Model is persisted on the domain task and shared execution.

The local voice placeholder drops `serviceConfigId` without adding `modelId`, because it performs no provider call. A future TTS implementation must add an audio-capable Model, registered execution handler, and provider adapter before accepting `modelId`.

Alternative considered: retain `serviceConfigId` as a deprecated alias. Rejected because the system is pre-launch and compatibility has no consumer value.

### 3. The unified invocation boundary owns provider transport

Business services no longer decrypt credentials, build provider URLs, select legacy adapters, or issue provider HTTP directly. Execution handlers call `AiInvocationService`; `AiModelRouter` validates Provider, Provider configuration, Model, and Capability, then supplies the registered adapter. Image and video domain services retain domain validation and result persistence but do not own transport configuration.

`AiModelRouter` removes `AiServiceConfigMapper` and `legacyConfig()`. `ProjectAiConfigService` validates only the platform Provider/Model/Capability state. `PlatformAiManagementService` removes `syncLegacyConfig()`.

Alternative considered: make each domain service read `ai_provider_config` directly. Rejected because it recreates routing, validation, logging, and error-mapping duplication.

### 4. Cleanup uses a destructive forward migration with explicit reset semantics

A new migration after V47 performs dependency cleanup in referential order. It removes capabilities, prices, project selections, and other references for Models with a non-null `legacy_service_config_id`, then deletes those legacy-derived Models. Historical nullable model references that point at removed Models are cleared; unrelated project, material, and result rows remain.

All Provider configuration credentials are cleared and Provider configurations are disabled because legacy synchronisation may have overwritten them and the schema does not record credential provenance. Platform administrators must explicitly re-enter credentials and enable Providers after deployment.

The migration adds `model_id` to provider-backed domain task tables where it is missing, drops `service_config_id` from task and call-log tables, drops `legacy_service_config_id` from `ai_model`, and drops `ai_service_test_log` and `ai_service_config`. New task creation requires a resolved Model in application validation; nullable database fields may remain where historical non-provider records require them.

Alternative considered: edit V4, V6, V9, V12, and V17 so fresh databases never see the legacy schema. Rejected because changing applied Flyway checksums makes existing developer databases unrecoverable without manual repair.

### 5. Configuration UI and operational visibility remain separate

The Service Config page, route, locale entry, request module, and tests are removed. Provider and Model pages remain platform-permission gated. The AI management index resolves to the first page the user can access: a platform administrator reaches Providers, while a tenant user with `AI_CALL_LOG:VIEW` reaches tenant-scoped logs and never sees configuration pages.

OpenAPI is regenerated with `npm run openapi` after backend contracts change; generated services are not manually edited.

Alternative considered: make the entire AI management route platform-only. Rejected because tenant-scoped call logs are operational data rather than configuration and may remain useful to authorized tenant users.

### 6. Missing configuration fails closed with canonical errors

Routing rejects missing, disabled, or unsupported Models and Providers before provider contact using the existing canonical AI error codes. No legacy credential fallback, mock endpoint fallback, or implicit Model substitution is allowed in production provider paths. A pre-contact validation failure creates no provider-success call log, usage cost, or point settlement; the shared execution records the normalized failure where an execution already exists.

## Risks / Trade-offs

- [Provider credentials currently have ambiguous origin] -> Clear and disable all Provider configurations, then require deliberate platform reconfiguration.
- [Legacy-derived Models may be referenced by projects or development tasks] -> Clear affected nullable references in the migration and require projects to select a valid platform Model before their next AI operation.
- [Removing request fields breaks frontend and generated clients] -> Change backend and handwritten frontend contracts together, regenerate OpenAPI clients, and run contract-focused tests before the full suites.
- [Video and local voice code currently depend deeply on legacy entities] -> Separate model resolution from domain persistence and keep local-only voice behavior explicitly outside provider execution.
- [A broad cleanup can leave hidden legacy references] -> Add architecture tests and repository searches that fail when production code or final schema still contains forbidden legacy symbols.

## Migration Plan

1. Add failing architecture and contract tests that prohibit legacy configuration dependencies and require model-based task contracts.
2. Update image and video task creation, regeneration, persistence, execution handlers, and frontend payloads to use resolved `modelId` through the unified invocation boundary.
3. Remove `serviceConfigId` from the local voice placeholder without adding provider execution or billing.
4. Remove legacy routing, project availability checks, call-log joins, synchronisation, controller/service/model types, and configuration permissions.
5. Remove the frontend Service Config feature, adjust access and route fallback, and regenerate the OpenAPI client.
6. Add the destructive forward migration, including credential reset and referential cleanup, and verify it against a schema containing representative legacy rows.
7. Run targeted tests, complete backend and frontend suites, type checking, Biome, Ant Design lint, production builds, and a final forbidden-symbol scan.
8. Re-enter Provider credentials and enable required Providers/Models before exercising AI workflows.

Rollback is code rollback plus database restoration from a pre-migration development snapshot. The deleted credentials and legacy configuration are intentionally not reconstructible from the forward migration.

## Open Questions

None. Platform-only ownership, destructive legacy-data removal, and explicit credential reconfiguration have been confirmed.
