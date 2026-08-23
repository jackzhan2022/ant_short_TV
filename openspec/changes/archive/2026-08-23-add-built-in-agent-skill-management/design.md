## Context

The backend already exposes `AiInvocationService`, `AiBusinessScene`, `PromptTemplateRenderer`, provider adapters, and platform model routing. Prompt bodies are currently embedded in `BuiltInPromptTemplateRenderer`, while the frontend already has an AI service management area with provider, model, service-config, and call-log views.

This change introduces a read-only, code-defined catalog of built-in Agents and Skills. An Agent is one supported business AI scene. A Skill is a reusable prompt capability composed into one or more Agents. Neither entity is user-configurable, persisted as editable data, versioned, enabled/disabled, or assigned a model override.

## Goals / Non-Goals

**Goals:**

- Define stable built-in Agent and Skill registries in the backend.
- Bind each Agent to one business scene, capability, fixed input/output contract, and ordered Skills.
- Render prompts from Agent content plus ordered Skill content.
- Preserve platform model routing and the existing unified AI invocation flow.
- Expose read-only catalog and prompt-preview APIs.
- Add one frontend page with Agent and Skill tabs under AI service management.
- Preserve the current intent, variables, and output contracts of existing built-in workflows.

**Non-Goals:**

- No user-created or user-edited Agents or Skills.
- No Agent or Skill persistence tables, versions, drafts, publishing, or audit history.
- No model selection stored on an Agent.
- No tool execution, memory, multi-step planning, or autonomous Agent runtime.
- No replacement of provider/model management or AI call-log management.

## Decisions

### Use code-defined registries

Agents and Skills will be represented by typed backend definitions or registries, similar to the current `AiBusinessScene` and built-in prompt templates. This matches the immutable product requirement and avoids introducing database state for data that cannot be edited.

The registry will expose stable codes, display metadata, capability, scene binding, prompt sections, variable definitions, output schema, and ordered Skill references. Unknown or duplicated references will fail application startup or registry validation rather than producing a partially rendered prompt.

### Treat Skills as reusable prompt modules

The first implementation treats a Skill as a prompt fragment with metadata, not as executable code or a tool. Agent composition concatenates the Agent prompt sections and Skills in deterministic order. This keeps the feature compatible with existing provider adapters and avoids creating a workflow engine.

### Bind business scenes to Agents

Business workflows continue to use stable `AiBusinessScene` values. Each supported scene resolves to exactly one built-in Agent. The Agent supplies prompt composition, while `AiBusinessScene` remains the stable business and logging contract.

### Follow platform model routing

Agents specify capability but never a model ID. `AiInvocationService` continues to resolve the model through `AiModelRouter`, so changing the platform default model affects all relevant Agents consistently.

### Provide read-only catalog APIs

The backend will expose authenticated, permission-gated read APIs for Agent and Skill lists, details, relationships, and rendered prompt previews. There will be no mutation endpoints. Preview requests validate supplied variables but do not call a provider or create an AI call log.

### Preserve invocation observability

Agent code and business scene code will be carried in invocation metadata and included in call-log request metadata where the current schema supports it. Existing provider, model, duration, token, error, and trace fields remain authoritative.

### Migrate existing templates incrementally

Existing hard-coded templates will first be represented by built-in Agent and Skill definitions without changing their text intent, required variables, or output structure. Workflow migrations can then replace direct template IDs with Agent resolution while retaining the existing `AiInvocationService` entry point.

## Risks / Trade-offs

- [Risk] A code-defined registry requires a deployment to change prompt content. → This is accepted because the requirement explicitly makes Agents and Skills immutable; prompt changes are product releases.
- [Risk] Composed prompt text may become difficult to inspect. → Provide deterministic ordering and a read-only final-prompt preview.
- [Risk] A registry code or scene binding can be accidentally removed during refactoring. → Add registry validation and contract tests for every supported business scene.
- [Risk] Existing prompt behavior may regress during migration. → Preserve current prompt fixtures and add rendered-output regression tests.
- [Risk] Logging schema may not have dedicated Agent fields. → Reuse existing business-scene/request metadata first and extend only if needed without changing log semantics.

## Migration Plan

1. Add registries, composition, validation, and read-only catalog APIs.
2. Add the two-tab frontend catalog and preview views.
3. Register current built-in workflows and verify rendered prompts against existing tests.
4. Migrate business workflow call sites scene by scene.
5. Remove only the now-unused direct prompt-template registrations after all callers use Agent resolution.

Rollback is a code rollback: existing provider routing and invocation APIs remain available, and the migration can be reverted before removing legacy template paths.

## Open Questions

- Whether the first release should expose the complete final prompt or only a redacted preview when input variables contain sensitive project content.
- Whether prompt-preview APIs should be available to all authenticated AI-management viewers or require a separate permission.
