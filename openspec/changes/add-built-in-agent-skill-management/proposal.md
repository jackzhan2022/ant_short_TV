## Why

The platform already centralizes AI provider and model routing, but reusable business prompts remain embedded in Java code and are difficult to inspect as a coherent capability. The system needs a read-only catalog of built-in Agents and Skills so operators and developers can understand which business AI scenes exist, how their prompt capabilities are composed, and which platform-routed capability each scene uses.

## What Changes

- Add a built-in Agent registry representing supported business AI scenes such as script rewriting, element extraction, video script decomposition, and script review.
- Add a built-in Skill registry containing reusable system prompt fragments for output formatting, factual constraints, entity consistency, domain rules, and review rules.
- Define fixed, ordered Agent-to-Skill relationships for each built-in Agent.
- Render Agent prompts from built-in Agent definitions and ordered Skills while preserving existing input-variable validation and unified AI invocation behavior.
- Add one AI management page with two tabs: Agent management and Skill management.
- Provide read-only list, detail, relationship, prompt-preview, input-schema, and output-schema views for built-in Agents and Skills.
- Keep Agent model selection on the existing platform routing path; Agents do not store a model override.
- Do not provide create, edit, delete, enable/disable, or version-management operations for Agents or Skills.
- Record the resolved Agent identity and business scene in AI invocation metadata and logs where the existing logging contract permits.

## Capabilities

### New Capabilities

- `built-in-agent-skill-catalog`: Provides built-in Agent and Skill definitions, fixed composition, prompt rendering, and read-only catalog APIs and UI.
- `agent-aware-ai-invocation`: Resolves a built-in Agent and its Skills before using the existing unified invocation and platform model-routing contract.

### Modified Capabilities

<!-- No existing baseline capability specification is modified by this change. -->

## Impact

- Backend AI domain: Agent and Skill registries, prompt composition, business-scene binding, read-only catalog endpoints, and invocation metadata.
- Frontend AI service management: a two-tab Agent/Skill catalog and detail/preview views.
- Existing built-in prompt rendering and current AI business scenes will be migrated without changing their intended inputs or outputs.
- Existing provider, model, routing, permissions, and AI call-log infrastructure will be reused.
- No new external dependency or user-editable persistence model is required.
