# AI schema migration verification

## Scope

`SchemaMigrationTest` runs all 48 Flyway migrations in H2 `MODE=MySQL` and verifies the execution, usage-cost, reservation, platform AI configuration, and domain-link structures. `LegacyAiConfigurationCleanupMigrationTest` starts at V47 with representative legacy-derived Models, project selections, tasks, logs, pricing, permissions, and unrelated business data, then verifies the destructive V48 cleanup. These are SQL-compatibility and forward-migration tests; they are not production rollback tests.

## Production-like MySQL rehearsal

1. Restore a recent MySQL backup containing representative legacy configuration, legacy-derived Models, project selections, image/video/voice tasks, call logs, pricing references, permissions, and unrelated business rows into an isolated database.
2. Record row counts and checksums for unrelated project, material, result, accounting, and user data. Separately record the legacy rows expected to be deleted or cleared.
3. Run `flyway migrate` with the release artifact. Confirm the schema reaches V48 and migration history is clean.
4. Verify legacy configuration and connectivity-test tables, task/call-log configuration columns, and legacy Model-link columns are absent. Verify legacy-derived Models and dependent capabilities/prices are deleted, nullable references are cleared, old configuration permissions are removed, `AI_SERVICE:USE` remains, and `AI_CALL_LOG:VIEW` exists.
5. Verify every Provider configuration is disabled with credential ciphertext and connectivity state cleared. Re-enter one Provider credential through the platform API/UI, run connectivity validation, enable compatible image/video Models, and confirm explicit, project-selected, and platform-default routing.
6. Smoke-test image and video execution, local voice placeholder behavior, tenant-scoped call-log access, redaction, usage-cost status, and point settlement. The voice placeholder must create no Provider invocation or accounting rows.
7. Compare unrelated row counts and checksums with the pre-migration snapshot. Any unexpected mismatch requires stopping rollout and restoring the snapshot.

## Rollback

V48 is intentionally destructive and has no down script. Rollback is a restore of the verified pre-migration MySQL snapshot (including binlogs if writes occurred), followed by deployment of the previous application version. Do not run `flyway clean` or manually reconstruct deleted credentials and legacy rows in a shared environment. After restore, verify configuration, Model, call-log, unrelated business, and point-ledger counts before reopening traffic.

