# AI Operations Runbook

## Execution and dispatch

Every production AI request creates an `ai_execution_task` before provider contact. Workers claim tasks atomically, perform network calls outside database transactions, and persist an `ai_execution_attempt` for each provider phase. A `RUNNING` task with an expired claim is recovered by the dispatcher; inspect the attempt and retry policy before replaying it.

## Provider configuration

Routing requires an enabled Provider, enabled Provider configuration with usable credentials, enabled Model, and an enabled `ai_model_capability` row matching the requested capability. Only platform administrators manage these records. Tenant permissions authorize use or tenant-scoped log inspection, never credential or Model management.

Migration V48 clears all Provider credential ciphertext, connectivity state, and disables Provider configurations. After deployment, a platform administrator must re-enter each required credential and base URL, run the real connectivity test, enable the Provider configuration, and verify compatible Models and capability rows are enabled. Provider-backed requests must remain fail-closed until this sequence is complete. Connectivity testing must never treat `mock://`, example endpoints, or test credentials as success.

## Pricing and points

Provider currency cost and tenant points are independent model-level prices. A platform administrator publishes them separately from `AI 服务管理 / 模型计费` after selecting an enabled Model. Publish requests contain the effective interval and metric components; the backend assigns the next version number independently for cost and point prices. Never supply or manually reuse a version number.

Published components are immutable. Only a `PUBLISHED` version whose effective start is still in the future can be revoked. To correct an effective or expired version, publish a later replacement. Revoked versions remain visible in history and are excluded from new execution resolution.

Before reservation or provider contact, every new execution must resolve both effective price versions and matching components for all required metrics and dimensions. A missing version or component is a platform configuration error: the request must fail closed without an execution, reservation, provider call, usage line, or cost line. Do not add zero-price fallback rules to bypass this check.

Successful preflight stores `cost_price_version_id` and `point_price_version_id` on the execution. Usage lines are immutable; cost lines use the frozen cost version and point reservations and settlement use the frozen point version. Infrastructure retries reuse those references, while intentional regeneration resolves versions again for the new execution version. `UNPRICED` means a legacy or inconsistent execution has no matching frozen cost component; `INCOMPLETE` means required usage is missing. Do not replace either status with zero. Point reservations settle exactly once and unexpected overage enters `SETTLEMENT_REVIEW_REQUIRED`.

For reconciliation, open the platform execution accounting detail and compare the frozen version IDs with the model billing history. Verify every usage line has a cost line with the expected `priceVersionId` and `priceComponentId`, then verify the reservation `pointPriceVersionId`, point component evidence, settled points, and ledger status. Treat a version mismatch, missing component, or usage/cost count mismatch as an accounting incident; preserve the records and correct pricing only with a new future version.

## Cancellation and retry

Cancellation before provider contact releases the reservation. Infrastructure retries reuse the execution version and idempotency key; intentional regeneration creates a new version and reservation. Never retry a successful or non-retryable terminal task.

## Security and retirement

Call-log request, response, and error summaries redact bearer credentials, API keys, secrets, and passwords before persistence. APIs expose masked Provider credentials only. The retired tenant configuration API, UI, permissions, tables, legacy Model linkage, and credential fallback must remain absent. Image and video operations route by platform `modelId`. The current local voice placeholder and shot or episode compose operations are local media processing: they have no Model, Provider call, call log, usage cost, or point settlement. A future provider-backed voice or compose implementation must be registered behind `AiInvocationService` and a durable execution handler before it is enabled.
