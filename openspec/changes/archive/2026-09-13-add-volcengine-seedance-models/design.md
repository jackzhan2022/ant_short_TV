## Context

The backend already routes video generation through platform Models, `AiModelRouter`, `AiInvocationService`, and the durable `AiVideoTaskService` lifecycle. `AiVideoProviderAdapter` currently uses a provider-neutral endpoint contract, while Volcengine Ark Seedance exposes provider-native asynchronous content-generation tasks. The requested Seedance 2.0 Fast, Seedance 2.0 Standard, and Seedance 2.5 variants need independent model identities but share one provider transport and one platform API key.

Endpoint IDs are deliberately unavailable during this change. The system must therefore ship the three model definitions as disabled entries and have no user-editable Endpoint-ID configuration surface.

## Goals / Non-Goals

**Goals:**

- Route all three Seedance variants through the existing `VIDEO_GENERATION` capability and durable video-task lifecycle.
- Implement Ark submit and poll transport behind a provider adapter, preserving normalized errors, call logs, result downloading, pricing, and point settlement.
- Give each variant a stable model code and a source-owned Endpoint-ID placeholder that can be replaced later without schema or UI changes.
- Keep the three models disabled until valid Endpoint IDs and Provider credentials are supplied.

**Non-Goals:**

- Adding a model-management field, API, or UI control for Endpoint IDs.
- Calling a provider from request threads, changing public video-task APIs, or replacing current task polling.
- Committing Ark API keys or fabricating Endpoint IDs.
- Adding other Volcengine models or changing video-understanding behavior.

## Decisions

### Use one `VOLCENGINE_ARK` provider and three fixed platform Models

The migration seeds a single provider with a canonical Ark base URL and three disabled Models: `SEEDANCE_2_0_FAST`, `SEEDANCE_2_0_STANDARD`, and `SEEDANCE_2_5`. Each carries a distinct source-owned Endpoint-ID placeholder in its model definition. They share provider credentials, but are independently selectable, enabled, priced, logged, and ordered.

An operator-entered per-model configuration field was considered and rejected because the requested workflow is to provide IDs after development, not to expose a new configuration contract. A shared model code was also rejected because it would lose variant-level routing, price, and audit identity.

### Implement native Ark asynchronous operations in a dedicated adapter

Create a Seedance-specific provider adapter that builds Ark content-generation submit requests from the existing video task (prompt, optional first-frame URL, duration, ratio, resolution and applicable controls), reads the external task ID, and polls the Ark task resource. The adapter returns the existing accepted/completed outcomes, so task state persistence remains in `AiVideoTaskService`.

Direct calls inside `AiVideoTaskService` were rejected because platform AI configuration requires provider contact to remain behind routing and invocation services. Extending the current generic adapter with Ark-specific branching was rejected because it would couple incompatible provider payloads and status schemas.

### Preserve current completion and storage ownership

Provider result URLs are treated as transient inputs. After a successful poll, the existing result path downloads the video into project-controlled object storage/local storage and stores the project-owned URL. Provider task IDs and request IDs are retained through the existing execution attempt and call-log fields.

### Fail closed before IDs exist

The migration inserts disabled models and does not designate a Seedance default. Adapter validation rejects a blank or placeholder Endpoint ID before HTTP contact. When IDs are supplied in source and the Provider API key is configured, an administrator still explicitly enables the Provider and selected Models through the existing authority model.

## Risks / Trade-offs

- [Ark request fields vary by Seedance version or Endpoint capability] → Centralize payload formation in the adapter, constrain fields to documented common options, and cover serialized payloads with HTTP-server tests.
- [Endpoint IDs are unavailable at release] → Ship disabled models and fail closed; update source placeholders only when real IDs are provided.
- [Provider result URLs expire] → Download results immediately through the existing result persistence flow rather than exposing provider URLs.
- [Provider statuses differ from local task states] → Map transient Ark states to `RUNNING`, terminal success to `SUCCEEDED`, and terminal failure/cancellation to `FAILED` while retaining raw status for diagnostics.
- [SDK version drift] → Use the existing JDK HTTP client instead of adding a volatile SDK dependency; the adapter owns the small Ark REST surface.

## Migration Plan

1. Deploy the database migration that creates the disabled Provider and three disabled Models/capabilities.
2. Deploy the adapter and focused tests. Existing video models and public APIs remain unchanged.
3. When supplied, replace the three source placeholders with official Endpoint IDs, configure the Ark API key through the existing Provider credentials flow, test connectivity, and explicitly enable the intended variants.
4. Roll back by disabling the Provider/models. The adapter and model data are additive, so existing video task records remain readable and no public API rollback is required.

## Open Questions

- The exact Endpoint IDs for the three variants are pending user input; placeholders must not be treated as callable models.
- The final Ark parameter support per Endpoint (such as allowed duration and resolution combinations) will be validated against the console configuration when IDs are supplied.
