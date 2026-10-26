# Volcengine Xinji supplier migration

This maintenance change creates `VOLCENGINE_ARK_XINJI`, copies the current Ark provider connection settings, and moves only `SEEDANCE_2_0_STANDARD` and `SEEDANCE_2_5`. Models keep their existing IDs, model codes, enablement, parameters, capabilities, concurrency quotas, and every price version/component. Mini and Fast remain assigned to `VOLCENGINE_ARK`.

The normal source implementation uses `VolcengineProviderCodes` in the router and Ark-specific video/free-creation branches. V155 performs the DML-only configuration migration. No new protocol or UI is introduced.

## Exact deployed release compatibility

Production and the shared working tree contain different changes. `prepare.py` preserves the exact deployed JAR and all its resources, libraries, and unrelated classes. `PatchArkSupplier.java` changes the router provider-code lookup and routes existing case-insensitive comparisons through the helper. The helper changes only comparisons whose expected value is `VOLCENGINE_ARK`; other comparisons retain `String.equalsIgnoreCase` behavior. This includes the production-only `VideoReferenceDiagnostics` class when present.

`VerifyArkSupplier.java` runs with JVM verification against classes extracted from the actual candidate. It checks both supplier identities, own connection configuration, original Ark adapter selection, native Seedance validation, token reservations, and pricing dimensions without contacting a provider or creating a billable task. Every non-target JAR entry must remain identical.

Prepare in a new server-side maintenance directory containing these scripts, `VolcengineProviderCodes.java`, and V155. Run `python3 prepare.py`; inspect `prepare.json`. `sudo python3 activate.py` requires unchanged production JAR hashes, unused migration V155, an absent new supplier, and no pending/running AI tasks. It takes exclusive deployment locks, saves a mode-0600 configuration snapshot, starts the candidate, and verifies the migration and unchanged prices. It creates `activation-receipt.json` containing no credentials.

## Recovery

The original release is preserved. The activation receipt records its exact path, new release, backup directory, and supplier ID. If activation fails, inspect service logs and the Flyway result before making changes. Before switching to a backend without the alias support, restore only the two moved model `provider_id` values from `before.private.json` in a transaction, checking the current IDs and supplier assignments. Do not overwrite model prices, parameters, task rows, or the copied supplier connection settings. Existing tasks under the new supplier must finish or retain a compatible backend. Do not blindly replay the private snapshot or delete migration history.

The preparation directory holds extracted libraries/classes and a candidate JAR for this specific release. Do not reapply it to a different release; prepare and verify a new candidate instead.
