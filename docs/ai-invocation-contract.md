# AI Invocation Contract

## Purpose

AI calls should enter business code through `AiInvocationService` instead of each workflow manually choosing provider routing, business scene strings, prompt construction, log writes, and error mapping.

The contract keeps provider adapters focused on transport and keeps workflow services focused on business state changes.

## Core Types

- `AiCapability`: typed model-routing capability. Current values include `TEXT`, `IMAGE`, `VIDEO_UNDERSTANDING`, `VIDEO`, and `AUDIO`.
- `AiBusinessScene`: stable business scene registry. Each scene owns its log code, display name, default capability, prompt template id when available, and point-consumption scene code.
- `AiInvocationRequest`: carries tenant/user/project/task ids, model override, scene, trace id, request payload, request summary, prompt template id, and template variables.
- `AiInvocationResult`: returns response payload, content summary, `aiCallLogId`, provider request id, resolved model/provider metadata, token usage, duration, and outcome status.

## Prompt Templates

Built-in prompt templates are rendered through `PromptTemplateRenderer`.

Built-in business scenes may resolve to immutable Agents in the built-in Agent registry. An Agent owns the business prompt contract and an ordered list of reusable Skills. Skills are prompt modules only; they do not execute tools or maintain memory. The Agent does not select a model and follows the platform capability router.

The Agent and Skill catalog is read-only. Definitions are changed through code releases and are exposed through the AI management page for inspection and final-prompt preview.

Current template ids:

- `script.element.character.extract`
- `script.element.scene.extract`
- `script.element.prop.extract`
- `video.understanding.analysis`
- `video.script.draft`

Current built-in Agent codes include:

- `script-rewrite`
- `script-character-extract`
- `script-scene-extract`
- `script-prop-extract`
- `video-understanding`
- `video-script-draft`
- `script-review`

Required variables are validated before provider calls. Missing variables fail with `VALIDATION_ERROR`, so the system does not create a misleading provider-success log for an invocation that never left the application.

## Call Logs

`AiCallLogWriter` is the single writer for `ai_call_log`.

It records:

- tenant/user/project task context
- capability as `service_type`
- stable business scene code
- model and provider metadata
- request and response summaries
- status and normalized error message
- duration, trace id, provider request id, and token usage when available

Provider transport success followed by business parsing failure should keep the original call log id and update that log to `FAILED` with `AI_RESPONSE_INVALID` context. This proves a real provider call happened while keeping the business outcome honest.

## Error Semantics

`AiInvocationErrorMapper` normalizes provider and business errors into existing `ErrorCode` values:

- auth failures: `AI_AUTH_FAILED`
- quota failures: `AI_QUOTA_EXCEEDED`
- rate limits: `AI_RATE_LIMIT`
- timeouts: `AI_PROVIDER_TIMEOUT`
- unavailable or unsupported provider/model/capability: existing AI unavailable codes
- invalid provider or business response: `AI_RESPONSE_INVALID`
- unexpected provider failures: `AI_PROVIDER_ERROR`

`AiGatewayException` can carry `aiCallLogId` for asynchronous attempts. Task executors should persist that id on failed attempts when present.

## Migration Rule

New AI workflows should:

1. Add or reuse an `AiBusinessScene`.
2. Add a built-in prompt template when the prompt is reusable or structured.
3. Consume points with `scene.pointScene()`.
4. Invoke AI through `AiInvocationService`.
5. Persist `AiInvocationResult.aiCallLogId()` and `providerRequestId()` on business attempts/results.
6. Mark business parsing failure through `AiInvocationService.markBusinessFailure(...)`.
