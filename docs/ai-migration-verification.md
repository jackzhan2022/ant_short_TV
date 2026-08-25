# AI schema migration verification

## Scope

`SchemaMigrationTest` runs all 47 Flyway migrations in H2 `MODE=MySQL` and verifies
the execution, usage-cost, reservation, legacy compatibility, and domain-link
structures. This is a SQL-compatibility and forward-migration test; it is not a
production rollback test.

## Production-like MySQL rehearsal

1. Restore a recent MySQL backup containing representative `ai_service_config`,
   `ai_model`, `ai_call_log`, `team_point_transaction`, image, script, review, and
   video rows into an isolated database.
2. Record row counts and checksums for those legacy tables, then run
   `flyway migrate` with the release artifact. Confirm the schema reaches V47,
   migration history is clean, and all recorded legacy rows remain readable.
3. Run the application smoke checks: model routing with capability rows, task
   creation/idempotency, call-log redaction, usage-cost status, point reservation
   settlement, and the operations overview query.
4. Capture the same row counts/checksums and compare them with the pre-migration
   snapshot. Any mismatch requires stopping rollout and restoring the snapshot.

## Rollback

Flyway migrations V35-V47 are additive or column-linking migrations and do not
ship destructive down scripts. Rollback is therefore a restore of the verified
pre-migration MySQL snapshot (including binlogs if writes occurred), followed by
deployment of the previous application version. Do not run `flyway clean` or
manually drop the new tables in a shared environment. After restore, verify the
legacy call-log and point-ledger counts before reopening traffic.

