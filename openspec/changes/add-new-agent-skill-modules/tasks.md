## 1. Persistence, permissions, and configuration

- [x] 1.1 Add a Flyway migration for independent workflow Agent configuration, ordered Skill associations, tool allowlists, Agent runs, and run steps with required unique keys, foreign keys, optimistic-lock fields, and query indexes.
- [x] 1.2 Add independent Agent（新）view/edit and Skill（新）view/edit permission constants and migration data, granting them only to the platform super-administrator role by default.
- [x] 1.3 Add typed backend configuration for the persistent Skill root, file size limits, Agent max-step/timeout limits, and run-log payload limits, including safe development defaults and production validation.
- [x] 1.4 Add migration and configuration tests proving legacy Agent/Skill tables, permissions, and data are unchanged.

## 2. File-backed Skill backend

- [x] 2.1 Add failing unit tests for safe Skill code/path resolution, UTF-8 SKILL.md parsing, required frontmatter, revision hashes, and rejection of traversal or malformed content.
- [x] 2.2 Implement the Skill document parser, normalized path resolver, and metadata/value objects needed to pass the validation tests.
- [x] 2.3 Add failing service tests for list/search/detail/create/update/copy/delete, atomic replacement, stale revision conflicts, referencing-Agent reporting, and referenced deletion protection.
- [x] 2.4 Implement the filesystem Skill repository and service with atomic create/replace/delete behavior, immediate cache invalidation or fresh reads, reference checks, and normalized safe errors.
- [x] 2.5 Add permission-focused controller tests, then implement the independent Skill（新）REST API and DTOs without exposing absolute server paths.

## 3. Workflow Agent configuration backend

- [x] 3.1 Add persistence mapper/repository tests for Agent CRUD, immutable unique code, optimistic locking, ordered Skill associations, tool associations, and transaction rollback on partial failure.
- [x] 3.2 Implement workflow Agent entities, repositories, association replacement, validation, audit metadata, copy behavior, enable/disable behavior, and business-reference deletion guard.
- [x] 3.3 Add service tests for missing or disabled models, missing/invalid Skills, unknown tools, invalid generation limits, disabled invocation, and prompt text that names an unassociated tool.
- [x] 3.4 Implement Agent validation and current-configuration loading against platform models, the file-backed Skill service, and the tool registry.
- [x] 3.5 Add permission and response-contract tests, then implement independent Agent（新）list/detail/create/update/copy/delete/enable/disable APIs with ordered associations.

## 4. Read-only tool registry and screenplay tools

- [x] 4.1 Add contract tests for ToolDefinition metadata, JSON input/output schemas, risk/failure policy, duplicate code detection, and the read-only catalog API.
- [x] 4.2 Implement the dependency-injected tool registry, schema validator, trusted ExecutionContext, executor contract, and permission-protected catalog endpoint.
- [x] 4.3 Add scope and output-contract tests for project context, episode list/read, adjacent episodes, script analysis, script assets, and screenplay format validation tools.
- [x] 4.4 Implement each tested read/validation tool through existing domain services with tenant, project, episode, and user authorization checks.
- [x] 4.5 Add transactional tests proving `save_episode_script` creates a version, selects it as current, retains prior versions, and rolls back both operations on failure.
- [x] 4.6 Implement `save_episode_script` through the existing episode version domain service and register only tools whose executors and schemas are complete.

## 5. Model tool-calling contract and Agent runtime

- [x] 5.1 Add provider-adapter and shared invocation contract tests for typed tool definitions, assistant tool calls, tool-result messages, final content, normalized errors, and backward compatibility with existing text-only invocations.
- [x] 5.2 Extend the shared AI invocation DTOs, router/adapter boundary, and supported provider adapters for model-native tool calling without bypassing existing call logging, cost accounting, or credential handling.
- [x] 5.3 Add runtime tests for ordered Skill composition, explicit tool exposure, schema validation, trusted scope injection, unassociated tool rejection, multi-step completion, step limit, timeout, and tool failure policy.
- [x] 5.4 Implement the workflow Agent runner and prompt composer using current saved configuration for formal runs and validated temporary configuration for test runs.
- [x] 5.5 Add persistence tests for redacted Agent/Skill/tool configuration snapshots, model call references, ordered run steps, final output, status transitions, timing, and normalized failures.
- [x] 5.6 Implement run and step audit persistence plus permission-protected formal-run, test-run, run-list, and run-detail APIs.

## 6. Frontend API layer and navigation

- [x] 6.1 Add frontend service types and mocked contract tests for Agent（新）, Skill（新）, tool catalog, test execution, and run-log endpoints.
- [x] 6.2 Implement handwritten frontend services for the new APIs without changing generated service files or legacy Agent/Skill services.
- [x] 6.3 Update model-management navigation tests for the seven-tab order, independent permissions, first-authorized-tab selection, and unchanged legacy tab rendering.
- [x] 6.4 Append permission-aware “Agent（新）” and “Skill（新）” tabs after the existing five tabs while preserving legacy routes and components.

## 7. Agent（新）management interface

- [x] 7.1 Add component tests for Agent list/search, create/edit/copy, immutable code, model and limit validation, enable/disable, reference-protected delete, and view-only behavior.
- [x] 7.2 Implement the Agent（新）list and editor with system prompt, model parameters, ordered Skill selector, tool allowlist, audit metadata, and immediate-save messaging.
- [x] 7.3 Add editor tests proving tool selection updates the allowlist and inserts readable text at the cursor while hand-typed tool codes do not create associations.
- [x] 7.4 Implement the cursor-aware tool insertion interaction and display tool schemas, risk levels, and selected authorization state.
- [x] 7.5 Add tests for saved and unsaved test runs, scope input, step timeline, errors, final output, and unchanged saved form state.
- [x] 7.6 Implement the Agent test drawer and run detail view with temporary configuration submission, loading/cancel states, redacted step logs, and normalized error display.

## 8. Skill（新）management interface

- [x] 8.1 Add component tests for Skill list/search, create/edit/copy, complete SKILL.md editing, frontmatter errors, revision conflicts, reference display, protected deletion, and view-only behavior.
- [x] 8.2 Implement the Skill（新）list and full-file editor with a valid starter template, immutable code, reference impact warning, revision token, and save-immediately messaging.
- [x] 8.3 Implement conflict and filesystem-error recovery that preserves the user's unsaved editor content and offers an explicit reload of the latest server file.

## 9. Verification and deployment readiness

- [x] 9.1 Run focused backend tests for new migrations, Skill filesystem behavior, Agent configuration, tool registry/tools, tool-calling adapters, runtime security, and audit persistence.
- [x] 9.2 Run focused frontend tests plus Biome/TypeScript checks for new tabs, permissions, editors, tool insertion, test runs, and legacy-tab regression coverage.
- [x] 9.3 Run backend and frontend full regression suites and verify no legacy Agent/Skill API, table, permission, route, or page behavior changed.
- [x] 9.4 Update deployment documentation with persistent Skill-root creation, ownership, backup/restore, release preservation, startup health checks, database migration order, smoke tests, and application-first rollback steps.
- [x] 9.5 Perform a local end-to-end smoke test that creates a Skill and Agent, edits the Skill without restart, runs the Agent with allowed read tools, saves a new current episode version, and inspects the complete run audit.
