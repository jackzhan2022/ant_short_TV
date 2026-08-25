# AI Operations Runbook

## Execution and dispatch

Every production AI request creates an `ai_execution_task` before provider contact. Workers claim tasks atomically, perform network calls outside database transactions, and persist an `ai_execution_attempt` for each provider phase. A `RUNNING` task with an expired claim is recovered by the dispatcher; inspect the attempt and retry policy before replaying it.

## Provider configuration

Routing requires an enabled provider, enabled provider configuration, enabled model, and an enabled `ai_model_capability` row matching the requested capability. Platform provider testing performs a real text connectivity request using an enabled text-capability model. It must never use `mock://`, example endpoints, or test credentials as a connectivity result.

## Pricing and points

Provider currency cost and tenant points are independent. Usage lines are immutable and cost lines snapshot the effective price version. `UNPRICED` means no matching price exists; `INCOMPLETE` means usage is missing. Do not replace either status with zero. Point reservations settle exactly once and unexpected overage enters `SETTLEMENT_REVIEW_REQUIRED`.

## Cancellation and retry

Cancellation before provider contact releases the reservation. Infrastructure retries reuse the execution version and idempotency key; intentional regeneration creates a new version and reservation. Never retry a successful or non-retryable terminal task.

## Security and retirement

Call-log request, response, and error summaries redact bearer credentials, API keys, secrets, and passwords before persistence. APIs expose masked provider credentials only. New reads do not create `ai_model` rows from legacy `ai_service_config`; migrate remaining legacy records ahead of removing compatibility routes, old gateways, and direct provider HTTP.
