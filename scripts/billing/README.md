# Versioned model billing maintenance

`model-billing-20261011.json` is the reviewed price plan for the ten online models. Prices are budget benchmarks, not verified reseller invoices. Text uses the DeepSeek peak baseline at a budget exchange rate of 7.5 CNY/USD; Qwen uses the conservative long-context tier; images use Seedream Pro's high-resolution output rate plus extra reference-image input; Seedance uses its published output-token tariffs by resolution and reference video.

Point rates are 210 times CNY budget cost. At minimum package revenue 49.9/6000 CNY per point, 20% variable-cost allowance, no consumption discount, and assumed 0.6% payment fees, budget contribution margin is approximately 30.69%. Actual supplier/operating costs and historical promotional credits can differ; this is not a net-profit guarantee.

`PublishBilling.java` is a standalone source launcher using the staged application's classes and dependency JARs. Its minimal Spring configuration imports only billing/package services and mappers. It excludes migrations and has no application component scan or business schedulers. Credentials must be supplied in the child process environment using `MYSQL_URL`, `MYSQL_USERNAME`, and `MYSQL_PASSWORD`; never put them in command arguments or output.

Modes: `plan.json check|apply|verify|rollback receipt.json`.

On the production host, place these files in a restricted maintenance directory and run `python3 run-maintenance.py /opt/antv/releases/<release> check` (replace the final mode for subsequent steps). The runner reads only database credentials from the shared environment into the child process. It binds extracted classes to the staged JAR's SHA-256 and publishes the extracted directory atomically, so retries cannot reuse a partial or different release runtime. Keep the release JAR immutable throughout maintenance.

- `check` confirms enabled model identities, expected active versions, valid units/multipliers, no active executions, subscription discounts and sale-price floor assumptions. It does not write prices.
- `apply` additionally requires `antv.service` to be inactive. It preserves a before snapshot, then publishes every cost/point pair and removes the two exact 0.01-CNY test offers from sale in one transaction. Historical amounts, entitlements, orders and ledgers are untouched. Publication timestamps are truncated to seconds to match MySQL DATETIME precision. Actual price resolvers and point calculations verify generic and dimension-specific cases before commit.
- `verify` checks the active model schedules through the real billing resolver and point calculator without paid provider calls or customer point mutations.
- `rollback` requires the service to be inactive and refuses if any execution references a new version. It revokes the new versions, restores the original activation endpoints and test-offer sale states in one transaction. Components remain as historical evidence.

Deployment order matters: validate/tests → stage new backend and frontend → check → drain active tasks → stop the old backend → apply prices using the new staged classes → atomically switch release → start backend → health and price verification. New code requires token/INPUT_IMAGE components; old handlers must never run against the new zero-CALL tariffs. On startup failure, stop the new service before invoking the guarded price rollback and restoring the previous release.

The before snapshot is retained after a failed transaction; a retry accepts it only if current prices still equal that snapshot. If database commit succeeds but receipt writing fails, inspect the preserved snapshot and actual version records manually; do not blindly repeat apply. No maintenance command repairs or retroactively bills historical unresolved executions.
