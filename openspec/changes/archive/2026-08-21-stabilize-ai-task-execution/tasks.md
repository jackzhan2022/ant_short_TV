## 1. Schema and Model Preparation

- [x] 1.1 Audit video decomposition and AI video task tables for existing execution metadata, attempt fields, status values, and indexes.
- [x] 1.2 Add Flyway migration for missing claim/idempotency metadata such as execution token, execution version, claimed timestamp, heartbeat or timeout timestamp, and retryability fields where needed.
- [x] 1.3 Update affected entities, mappers, and response DTOs with nullable backward-compatible fields.
- [x] 1.4 Add schema migration tests covering the new columns, indexes, and compatibility with existing seeded data.

## 2. Shared AI Task Reliability Component

- [x] 2.1 Add a shared backend component for atomic task claiming using conditional database updates.
- [x] 2.2 Add idempotency key construction for workflow type, task id, phase, and execution version.
- [x] 2.3 Add reusable attempt lifecycle helpers for pending, running, succeeded, failed, skipped, and timed-out attempts.
- [x] 2.4 Add retryability helper interfaces so each workflow can define legal retry phases and states.
- [x] 2.5 Add unit tests for claim races, duplicate idempotency keys, retry eligibility, and timeout recovery decisions.

## 3. Video Decomposition Integration

- [x] 3.1 Update `VideoDecompositionTaskScheduler` to claim pending analysis and draft-generation work before calling the execution service.
- [x] 3.2 Update `VideoDecompositionExecutionService` so provider calls happen only after a successful claim and use the accepted attempt/idempotency metadata.
- [x] 3.3 Update video decomposition retry behavior to reject confirmed, successful, running, deleted, or otherwise non-retryable episodes.
- [x] 3.4 Preserve successful sibling episodes and previous successful analysis/draft results during retry or regeneration.
- [x] 3.5 Expose latest attempt diagnostics and retryability in video decomposition detail responses without breaking existing fields.
- [x] 3.6 Add backend tests for concurrent scheduler polling, duplicate execution suppression, retry rejection, business parsing failure logging, and timeout recovery.

## 4. AI Video Task Integration

- [x] 4.1 Review `AiVideoTaskService` and `AiVideoTaskScheduler` submit/query phases and map them onto the shared reliability component.
- [x] 4.2 Add atomic claiming around due AI video submission or polling work before provider calls.
- [x] 4.3 Add idempotency and attempt diagnostics for AI video provider submit/query calls where schema support exists.
- [x] 4.4 Add tests ensuring repeated scheduler polling does not duplicate AI video provider calls.

## 5. Verification and Documentation

- [x] 5.1 Run backend Flyway/schema tests and targeted video decomposition and AI video task test suites.
- [x] 5.2 Run the full backend test suite.
- [x] 5.3 Update operational documentation with task states, retry rules, timeout behavior, and how to inspect linked AI call logs.
- [x] 5.4 Document any follow-up workflows, such as applying the same reliability component to AI image, shot voice, shot compose, and episode compose tasks.
