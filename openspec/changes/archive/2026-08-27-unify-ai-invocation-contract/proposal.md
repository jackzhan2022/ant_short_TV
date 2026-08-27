## Why

AI invocation is now spread across text generation, element extraction, image/video tasks, and Qwen video decomposition. Each caller chooses capability strings, business scene names, prompt construction, call-log writes, and provider error handling differently, making it hard to prove whether a feature made a real AI call or why a task succeeded at transport level but failed in business parsing.

This change introduces a unified invocation contract so business modules call AI through one typed boundary, with consistent capabilities, scene metadata, prompt template rendering, call logging, and normalized errors.

## What Changes

- Add a backend AI invocation contract that represents capability, business scene, model selection, prompt template, request payload, task linkage, and trace metadata in one request object.
- Add a central business scene registry for stable scene codes such as script generation, script rewrite, element extraction, prompt generation, video understanding, and video script draft generation.
- Add a prompt template entrypoint for built-in templates and variable rendering so callers no longer hand-build long prompts inline.
- Move `ai_call_log` persistence into a reusable logging service that records success, provider failure, and business parsing failure consistently across text, image, and video-understanding calls.
- Add a normalized error mapper for provider exceptions, unsupported capabilities, invalid responses, timeouts, rate limits, and quota/auth failures.
- Migrate the highest-risk existing callers first: script text calls, script element extraction, Qwen video understanding, and video script draft generation.
- Preserve current external APIs and database tables; no breaking frontend or client contract changes are planned.

## Capabilities

### New Capabilities

- `ai-invocation-contract`: Unified backend contract for AI capabilities, business scenes, prompt templates, call logs, and error normalization.

### Modified Capabilities

- `ai-task-execution-reliability`: Link asynchronous AI task attempts to the unified invocation result and normalized business/provider outcomes.

## Impact

- Backend AI package: `com.antshorttv.ai` gateway, routing, provider adapters, call-log code, and new invocation/prompt/scene/error abstractions.
- Backend business callers: script workflow services and video decomposition execution/gateway code.
- Database: existing `ai_call_log` table remains the source of truth; additive columns or indexes may be added only if needed for scene/capability/result diagnostics.
- Tests: unit and integration tests for invocation logging, prompt rendering, error mapping, and migrated script/video callers.
- Operations: logs become easier to query by stable capability, business scene, task id, trace id, provider request id, and normalized failure code.
