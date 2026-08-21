## Context

The backend already has an AI management layer with providers, models, routing, and `ai_call_log`, but invocation behavior is not expressed as one business contract. `DefaultAiGateway` logs text and image calls directly, while `VideoUnderstandingGateway` duplicates most call-log persistence for Qwen video understanding. Script generation, element extraction, prompt generation, and video decomposition also hard-code business scene strings and prompt text close to workflow code.

Recent failures in script element extraction and video decomposition showed the same operational gap: a provider call can be logged as successful while the business feature still has no usable result, and callers must query logs indirectly to understand what happened. The unified contract should preserve current external APIs while making internal invocation behavior explicit, typed, and testable.

## Goals / Non-Goals

**Goals:**

- Provide one backend service for business modules to invoke AI capabilities with typed metadata.
- Centralize capability and business scene codes.
- Centralize prompt template lookup and variable rendering for built-in workflow prompts.
- Centralize `ai_call_log` creation and update semantics, including provider success, provider failure, and business parsing failure.
- Normalize AI provider and business parsing errors into stable `ErrorCode` values and diagnostic fields.
- Migrate existing script and Qwen video decomposition callers without changing frontend routes or public API contracts.

**Non-Goals:**

- Do not replace provider adapters or the current model/provider configuration UI.
- Do not introduce a database-backed prompt template management UI in this change.
- Do not redesign asynchronous task claiming, retry, or scheduling semantics already covered by `ai-task-execution-reliability`.
- Do not change generated frontend service files or require frontend users to call a new API.
- Do not remove existing `AiGateway` methods until migrated callers and tests prove the unified contract can cover them.

## Decisions

### 1. Add a business-facing `AiInvocationService`

Create a new service in `com.antshorttv.ai` that accepts typed invocation requests and returns typed invocation results. Business modules call this service instead of directly choosing `AiGateway.text`, `VideoUnderstandingGateway.call`, or manual log lookup.

The request contains tenant/user/project/task identifiers, capability, business scene, trace id, model override, prompt template reference, rendered payload, and optional task phase metadata. The result contains response payload, provider request id, `aiCallLogId`, resolved model/provider data, token usage, duration, and normalized outcome.

Alternative considered: expand `AiGateway` itself into the business contract. That would mix provider transport concerns with business scene and prompt concerns, making adapters harder to test and keeping workflow metadata in a low-level interface.

### 2. Keep provider adapters focused on provider transport

Provider-specific classes continue to translate request/response formats. They should not decide business scene names, task phases, prompt templates, or call-log lifecycle. `VideoUnderstandingGateway` can be folded into the invocation service as a specialized capability executor or become a thin adapter wrapper during migration.

Alternative considered: make every provider adapter write `ai_call_log`. That would duplicate persistence and make business parsing failures difficult to update after a successful provider response.

### 3. Introduce typed capability and scene definitions

Add enums or value objects for AI capabilities and business scenes. Capabilities map to model routing strings such as `TEXT`, `IMAGE`, and `VIDEO_UNDERSTANDING`; business scenes map to stable codes such as `script_generate`, `script_rewrite`, `character_extract`, `scene_extract`, `prop_extract`, `prompt_generate`, `video_understanding`, and `video_script_draft`.

The scene definition owns display name, default capability, default prompt template id when applicable, and point-consumption scene code. Callers pass a scene constant instead of raw strings.

Alternative considered: keep strings and add constants beside each service. That would reduce typing but would not solve cross-module consistency or discoverability.

### 4. Add a built-in prompt template registry

Create a lightweight prompt template renderer for built-in templates. Templates can be Java resources or typed classes; variables are explicit and validated before rendering. Initial templates cover script element extraction and video decomposition prompts because those currently contain long inline prompts and strict JSON output requirements.

Database prompt template management remains out of scope, but the contract should make a future database-backed resolver possible by depending on a small `PromptTemplateResolver` interface.

Alternative considered: introduce full prompt-template CRUD now. The current need is invocation consistency and maintainability, so UI-backed template management would be too broad for this change.

### 5. Centralize call-log lifecycle

Extract `AiCallLogWriter` or equivalent from `DefaultAiGateway` and `VideoUnderstandingGateway`. The writer records a pending or final call entry with consistent service type, business scene, request summary, response summary, provider request id, token fields, duration, task id, trace id, model id, and provider id.

For provider transport success followed by business parsing failure, the invocation result remains linked to the original call log and the log is updated to a normalized business failure status/message. This preserves proof of the real external call while accurately reflecting unusable business output.

Alternative considered: leave successful transport logs untouched and only fail task attempts. That makes task state correct but keeps the AI call log misleading for operations and user debugging.

### 6. Normalize errors at the invocation boundary

Add a mapper that converts provider exceptions, unsupported capabilities, invalid provider responses, timeouts, rate limits, quota failures, auth failures, and business parsing failures into existing `ErrorCode` values plus a stable diagnostic category. Business services can still decide task retryability, but they should receive a normalized invocation failure instead of parsing provider-specific exceptions.

Alternative considered: add more `catch` blocks in each workflow service. That keeps changes local but perpetuates inconsistent failure states and log messages.

### 7. Migrate callers incrementally

Start with the highest-risk callers: script generate/rewrite gateway calls, script element extraction, video understanding, and video script draft generation. Image/video generation task code can keep its existing public behavior but should reuse the call-log writer and typed capability definitions when touched by this change.

This avoids a large rewrite while establishing the boundary future AI features must use.

## Risks / Trade-offs

- [Unified service becomes too broad] → Keep provider transport, prompt rendering, logging, and business parsing as separate collaborators behind the invocation service.
- [Scene registry misses an existing string] → Add tests that enumerate migrated callers and assert the expected stable scene code in `ai_call_log`.
- [Prompt rendering changes model output] → Keep template text semantically equivalent during migration and add tests for required JSON field instructions.
- [Business parsing failure overwrites useful provider details] → Preserve provider request id, token usage, and response summary while adding normalized business failure details.
- [Async retry behavior changes unintentionally] → Do not change claim/idempotency code; only replace how an accepted attempt invokes AI and receives `aiCallLogId`.
- [Migration touches too many files] → Migrate script and video decomposition first, leaving unrelated AI image/video generation flows on the old gateway except for shared logging internals.

## Migration Plan

1. Add typed capability, business scene, invocation request/result, prompt template, log writer, and error mapper classes with isolated tests.
2. Refactor `DefaultAiGateway` and Qwen video understanding logging to use the shared writer without changing external method signatures.
3. Add `AiInvocationService` and route text plus video-understanding calls through it.
4. Migrate script workflow, script element extraction, and video decomposition draft/understanding calls to the invocation service.
5. Run targeted backend tests for AI gateway/logging/script/video decomposition, then the backend test suite.
6. Rollback strategy: callers can be temporarily switched back to existing `AiGateway`/`VideoUnderstandingGateway` methods because the change preserves database tables and public API contracts.

## Open Questions

- Whether `ai_call_log.status` should keep only `SUCCESS`/`FAILED` or add a distinct value for business parsing failure. The default design uses existing values and records normalized detail in the error message unless an additive status is approved.
- Whether future database-backed prompt templates should reuse the same template ids introduced here; the design assumes yes and keeps ids stable.
