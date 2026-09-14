## 1. Data Model and Migration

- [x] 1.1 Add V59+ migrations for versioned Agent and Skill definitions, model parameter profiles, and task configuration snapshots
- [x] 1.2 Add entities, mappers, repositories, validation objects, and seed published records from current built-in definitions

## 2. Backend Management APIs

- [x] 2.1 Implement authorized CRUD, draft/publish, preview, enable/disable, and rollback APIs for Agent and Skill definitions
- [x] 2.2 Implement model parameter profile APIs with server-side range validation and audit metadata
- [x] 2.3 Add tests for permissions, version conflicts, validation, publishing, rollback, and snapshot immutability

## 3. AI Invocation Integration

- [x] 3.1 Resolve published Agent/Skill definitions and model parameters when creating a script analysis task
- [x] 3.2 Persist and reuse configuration snapshots across stages and retries
- [x] 3.3 Extend text request payloads with top-p and JSON response format when supported
- [x] 3.4 Capture finish reason, response length, truncation, and granular structured-output errors
- [x] 3.5 Add integration/unit tests proving configured prompts and parameters reach the provider adapter

## 4. Frontend Management UI

- [x] 4.1 Add editable Agent management tab with draft, preview, publish, enable/disable, and rollback flows
- [x] 4.2 Add editable Skill management tab with association display and lifecycle controls
- [x] 4.3 Add model parameter configuration form with inline range validation and JSON mode toggle
- [x] 4.4 Regenerate or extend API clients, loading states, error feedback, and permission-aware controls

## 5. Verification and Rollout

- [x] 5.1 Run backend and frontend test suites plus migration validation
- [x] 5.2 Verify a long-script analysis with max tokens above 2048 and JSON mode enabled
- [x] 5.3 Deploy to staging, inspect AI call logs and costs, then document production rollback steps
