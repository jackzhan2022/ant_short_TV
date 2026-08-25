# AI Operations Runbook

## Execution and dispatch

Every production AI request creates an `ai_execution_task` before provider contact. Workers claim tasks atomically, perform network calls outside database transactions, and persist an `ai_execution_attempt` for each provider phase. A `RUNNING` task with an expired claim is recovered by the dispatcher; inspect the attempt and retry policy before replaying it.

## Provider configuration

Routing requires an enabled Provider, enabled Provider configuration with usable credentials, enabled Model, and an enabled `ai_model_capability` row matching the requested capability. Only platform administrators manage these records. Tenant permissions authorize use or tenant-scoped log inspection, never credential or Model management.

Migration V48 clears all Provider credential ciphertext, connectivity state, and disables Provider configurations. After deployment, a platform administrator must re-enter each required credential and base URL, run the real connectivity test, enable the Provider configuration, and verify compatible Models and capability rows are enabled. Provider-backed requests must remain fail-closed until this sequence is complete. Connectivity testing must never treat `mock://`, example endpoints, or test credentials as success.

## Pricing and points

Provider currency cost and tenant points are independent. Usage lines are immutable and cost lines snapshot the effective price version. `UNPRICED` means no matching price exists; `INCOMPLETE` means usage is missing. Do not replace either status with zero. Point reservations settle exactly once and unexpected overage enters `SETTLEMENT_REVIEW_REQUIRED`.

## Cancellation and retry

Cancellation before provider contact releases the reservation. Infrastructure retries reuse the execution version and idempotency key; intentional regeneration creates a new version and reservation. Never retry a successful or non-retryable terminal task.

## Security and retirement

Call-log request, response, and error summaries redact bearer credentials, API keys, secrets, and passwords before persistence. APIs expose masked Provider credentials only. The retired tenant configuration API, UI, permissions, tables, legacy Model linkage, and credential fallback must remain absent. Image and video operations route by platform `modelId`; the current local voice placeholder has no Model, Provider call, call log, usage cost, or point settlement.
