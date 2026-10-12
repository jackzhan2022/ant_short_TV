# Published package configuration — 2026-10-12

This directory archives the exact maintenance scripts used to publish the monthly card and three point packs. Results and version IDs are in `docs/commercial-packages-release-2026-10-12.md`; cost assumptions are in `docs/model-cost-market-audit-2026-10-11.md`.

`PublishPackages.java` calls the existing commercial package services in a minimal Spring context without application schedulers. It validates the previous package state and margin assumptions, creates and publishes four versions, takes old version 5 off sale, and verifies the actual sale catalog in one transaction. It does not create orders or grant points.

`run-packages.py` compiles and runs that helper against the exact deployed backend runtime. Database credentials are read from the production environment file into process memory. Package snapshots, receipts, database credentials, and generated classes are not source artifacts.

The production maintenance directory is `/home/ubuntu/antv-packages-20261012`. Supported modes:

```sh
# Read-only verification of the completed publication:
python3 /home/ubuntu/antv-packages-20261012/run-packages.py verify

# Historical entry points, intended only for the original pre-publication state:
python3 /home/ubuntu/antv-packages-20261012/run-packages.py check
python3 /home/ubuntu/antv-packages-20261012/run-packages.py apply
```

Publication is already complete. The original-state checks intentionally reject another `apply`; do not remove them to repeat publication. The runner also rejects a different current backend release and binds its classpath to the original runtime checksum.

`verify-history.py` compares before/after order, ledger, balance and subscription aggregates and checks backend health. Its original deployment expects `package-preflight.sql` and the repository's `scripts/production-task-acceptance/mysql_check.py` in `/tmp/antv-billing-20261012`, plus `audit-before.json` in the maintenance directory. The read-only SQL is included here; the database snapshots remain on the production host.
