# AI Task API Compatibility

## Stable task contract

New production AI operations use durable execution tasks. Domain create endpoints return HTTP 202 with an
`AiExecutionResponse`; clients then use the tenant-scoped detail and control endpoints under
`/api/tenants/{tenantId}/ai-executions`. These responses carry `X-AI-Task-Contract-Version: 1`.

Version 1 defines canonical task status, phase, progress, retryability, normalized errors, domain result
references, provider-cost state, point-settlement state, and lifecycle timestamps. Provider-specific
submission or polling states are not part of this public contract.

## Legacy synchronous compatibility

Existing domain AI routes keep their current paths and response DTOs until the corresponding image, script,
review, video, voice, or composition workflow is migrated together with its frontend caller. A migrated route
may temporarily adapt its old DTO from the durable task, but all new integrations must consume the task
contract. Administrative connectivity tests and prompt previews remain synchronous diagnostics.

Compatibility routes must not be removed or silently change response shape during an incremental workflow
deployment. Generated frontend services are regenerated only after the backend OpenAPI contract stabilizes;
generated files are never edited by hand.

## Breaking cutover

Removing legacy synchronous responses is a separate breaking API release. Before that release, every caller
must create or reuse a durable task, handle HTTP 202, persist the execution ID, poll after page reload, and load
the linked domain result after success. The release must publish migration notes, remove compatibility routes,
increment the task contract version when its response semantics change, and regenerate all OpenAPI clients.

There is no scheduled sunset date yet. A date must not be advertised until all workflow migrations and
production reconciliation checks are complete.
