## 1. Contract Types and Scene Registry

- [x] 1.1 Add typed AI capability definitions and map them to current model routing capability strings.
- [x] 1.2 Add stable AI business scene definitions for script generation, script rewrite, element extraction, prompt generation, video understanding, and video script draft generation.
- [x] 1.3 Add invocation request/result value objects carrying tenant, user, project, task, trace, model override, capability, scene, payload, summaries, and outcome metadata.
- [x] 1.4 Add tests that validate supported capability mappings and required scene metadata.

## 2. Prompt Template Entrypoint

- [x] 2.1 Add a prompt template resolver/renderer abstraction for built-in templates with explicit variable validation.
- [x] 2.2 Move script element extraction prompts into built-in templates without changing required JSON output instructions.
- [x] 2.3 Move video understanding and video script draft prompts into built-in templates without changing current prompt intent.
- [x] 2.4 Add tests for successful rendering, missing variables, and strict JSON instruction preservation.

## 3. Logging and Error Normalization

- [x] 3.1 Extract shared AI call-log writer from `DefaultAiGateway` and `VideoUnderstandingGateway` behavior.
- [x] 3.2 Support success, provider failure, and business parsing failure updates while preserving provider request id, token usage, duration, task id, trace id, model id, and provider id.
- [x] 3.3 Add normalized invocation error mapping for auth, quota, rate limit, timeout, unsupported capability/model, provider error, validation, and invalid response cases.
- [x] 3.4 Add tests for text, image, video-understanding, provider failure, and business parsing failure log records.

## 4. Unified Invocation Service

- [x] 4.1 Implement `AiInvocationService` as the business-facing AI call entrypoint using model routing, provider adapters, prompt rendering, log writer, and error mapper.
- [x] 4.2 Route text invocations through the unified service and return content plus invocation metadata.
- [x] 4.3 Route Qwen video-understanding invocations through the unified service while keeping provider-specific request formatting in the Qwen adapter.
- [x] 4.4 Keep existing gateway methods compatible as thin wrappers or transitional paths for non-migrated callers.

## 5. Caller Migration

- [x] 5.1 Migrate script generation and rewrite calls to use `AiInvocationService` and stable scene definitions.
- [x] 5.2 Migrate script element extraction calls to use prompt templates, stable scene definitions, and unified invocation results.
- [x] 5.3 Migrate prompt generation logging calls to use stable scene definitions without changing existing generated prompt output.
- [x] 5.4 Migrate video decomposition video understanding and video script draft calls to use unified invocation results for provider request id and AI call log id.
- [x] 5.5 Ensure point consumption scene codes match the AI invocation business scene codes for migrated workflows.

## 6. Async Attempt Integration

- [x] 6.1 Update video decomposition attempts to record provider request id and AI call log id from unified invocation results.
- [x] 6.2 Ensure provider failures and business parsing failures set normalized error codes on attempts and tasks.
- [x] 6.3 Add tests that verify async attempts remain linked to AI call logs for provider success, provider failure, and business parsing failure.

## 7. Verification and Documentation

- [x] 7.1 Add or update targeted tests for migrated script workflow, element extraction, video understanding, and video draft generation paths.
- [x] 7.2 Run targeted backend AI/script/video tests.
- [x] 7.3 Run the backend test suite.
- [x] 7.4 Document the unified invocation contract, scene registry, prompt template entrypoint, and log/error semantics for future AI features.
