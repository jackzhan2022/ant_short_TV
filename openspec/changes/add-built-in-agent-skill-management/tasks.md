## 1. Backend Registry And Composition

- [x] 1.1 Define immutable built-in Agent and Skill metadata types, including stable codes, display metadata, capability, scene binding, prompt sections, variables, and output contracts.
- [x] 1.2 Define the built-in Skill catalog and fixed ordered Agent-to-Skill mappings for script rewriting, character extraction, scene extraction, prop extraction, video understanding, video script drafting, and script review.
- [x] 1.3 Implement registry validation for duplicate codes, missing scene bindings, unknown Skill references, invalid ordering, and incomplete input/output contracts.
- [x] 1.4 Implement Agent prompt composition with deterministic Agent-plus-Skill ordering and reuse existing required-variable validation semantics.
- [x] 1.5 Add unit tests for registry validation, Skill ordering, variable substitution, missing variables, and rendered prompt fixtures.

## 2. Unified AI Invocation Integration

- [x] 2.1 Add business-scene-to-Agent resolution through the existing AI invocation boundary without introducing Agent-specific model selection.
- [x] 2.2 Migrate current built-in prompt workflows to Agent resolution while preserving their existing input variables, output structures, and business parsing behavior.
- [x] 2.3 Keep model selection on `AiModelRouter` platform capability routing and preserve existing provider adapter, error mapping, points, and call-log behavior.
- [x] 2.4 Include resolved Agent and business-scene context in invocation metadata or request summaries supported by the current logging contract.
- [ ] 2.5 Add integration and regression tests covering character extraction, scene extraction, prop extraction, video understanding, video script drafting, and script review.

## 3. Read-Only Backend APIs

- [x] 3.1 Add permission-gated APIs for listing and retrieving built-in Agents and their ordered Skills.
- [x] 3.2 Add permission-gated APIs for listing and retrieving built-in Skills and their referencing Agents.
- [x] 3.3 Add a prompt-preview API that validates variables and returns the composed prompt without calling a provider or writing an AI call log.
- [x] 3.4 Add controller tests for catalog responses, relationships, preview validation, and absence of mutation operations.

## 4. Frontend Agent And Skill Catalog

- [x] 4.1 Add the AI management route/menu entry for the read-only Agent and Skill catalog using existing permission conventions.
- [x] 4.2 Build the two-tab page with Agent list/detail and Skill list/detail views, including scene, capability, relationships, and immutable built-in indicators.
- [x] 4.3 Build Agent prompt preview with generated input controls from the Agent input contract and display the composed prompt and output contract.
- [x] 4.4 Add frontend service types and request helpers for Agent/Skill catalog and preview APIs.
- [x] 4.5 Add frontend tests for the read-only Agent/Skill page shell and route, plus service-backed catalog/preview integration points.

## 5. Verification And Documentation

- [x] 5.1 Document the built-in Agent and Skill registry contract and how new built-ins are added through code releases.
- [ ] 5.2 Run backend tests and type/lint checks for changed modules.
- [ ] 5.3 Run frontend tests, type checks, Biome lint, and Ant Design lint for changed modules.
- [ ] 5.4 Verify the OpenSpec requirements against implemented behavior and confirm no create/edit/delete/enable/disable/version APIs exist.
