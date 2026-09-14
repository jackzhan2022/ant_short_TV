## Why

The platform lacks first-class Volcengine Seedance video-generation models, preventing the Fast, Standard, and 2.5 variants from using the existing model routing and asynchronous video-task pipeline. This change prepares the three variants for use without waiting for their Endpoint IDs.

## What Changes

- Add a Volcengine Ark provider adapter for Seedance asynchronous video generation and task polling.
- Add fixed model definitions for Seedance 2.0 Fast, Seedance 2.0 Standard, and Seedance 2.5.
- Keep each model disabled until its Endpoint ID is supplied and placed in the application-owned model definition; do not add a new backend configuration field or management-page control.
- Map Seedance submission, polling, provider errors, and result URLs into the existing video execution and AI call-log contracts.
- Add focused backend tests for model routing, request payloads, accepted/completed states, and provider failures.

## Capabilities

### New Capabilities

- `volcengine-seedance-video-generation`: Fixed Seedance model variants and their asynchronous Ark video-generation behavior.

### Modified Capabilities

- `platform-ai-configuration`: Extend the platform's built-in provider/model catalogue with the Volcengine Ark provider and the three disabled Seedance definitions.

## Impact

- Backend AI provider adapter, model routing, asynchronous video-task execution, result storage handoff, and AI call logging.
- Database migration/seed data for provider and model records; no breaking API changes.
- Runtime API-key configuration only; Endpoint IDs are supplied later as source-owned model-definition values and no credentials are committed.
