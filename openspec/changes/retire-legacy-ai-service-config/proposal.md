## Why

The application currently has two competing AI configuration sources: legacy tenant-facing `ai_service_config` records and the platform Provider/Model configuration used by the unified AI gateway. Because the product has not launched and legacy development data may be discarded, consolidating now avoids permanent split-brain routing, credential ownership, permissions, and deletion behavior.

## What Changes

- **BREAKING** Remove the tenant-facing AI service configuration pages, APIs, permissions, persistence model, connectivity-test records, and generated client contracts.
- Make platform-managed Provider credentials, Models, and Model Capabilities the only AI configuration source; only platform administrators may manage them.
- **BREAKING** Replace `serviceConfigId` with `modelId` in provider-backed image and video task contracts, and remove `serviceConfigId` from the current local-only voice placeholder contract.
- Route all production AI workflows through `AiModelRouter` and `AiInvocationService`, with project model selection followed by the platform default model when no explicit model is requested.
- Remove legacy model synchronization, `legacyServiceConfigId`, direct reads of `ai_service_config`, and fallback to legacy credentials or endpoints.
- Add a destructive forward migration that discards legacy service configuration data, removes legacy-derived models and columns, clears provider credentials for explicit platform reconfiguration, and drops the legacy tables.
- Keep tenant AI usage authorization and execution/call-log visibility separate from platform configuration authority.

## Capabilities

### New Capabilities

- `platform-ai-configuration`: Defines platform-only ownership of Provider credentials and Models, model-based workflow selection and routing, fail-closed configuration behavior, and removal of tenant AI service configuration.

### Modified Capabilities

None.

## Impact

- Backend AI configuration, routing, invocation, image, video, local voice placeholder, project model selection, call-log, RBAC, and generated OpenAPI contracts.
- Frontend AI management routes, access rules, Provider/Model pages, production-workbench request/response types, locales, and generated services.
- MySQL/Flyway schema for `ai_service_config`, `ai_service_test_log`, AI task tables, call logs, and `ai_model.legacy_service_config_id`.
- Existing development credentials and legacy-derived model records are intentionally discarded; platform administrators must configure Provider credentials and Models again after migration.
