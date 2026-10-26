# Volcengine Xinji supplier split - 2026-10-26

Production activation completed at 16:16:10 Asia/Shanghai. Supplier `VOLCENGINE_ARK_XINJI` is named 火山（信计） and has ID 8.

| Model | Model ID | Supplier ID |
| --- | --- | --- |
| Seedance 2.0 mini | 11 | 6 (original Ark supplier) |
| Seedance 2.0 Fast | 4 | 6 (original Ark supplier) |
| Seedance 2.0 Standard | 5 | 8 (Xinji) |
| Seedance 2.5 | 6 | 8 (Xinji) |

Only the provider IDs of Standard and 2.5 were updated. The new supplier copies the original provider metadata and connection configuration, including the encrypted API key, base URL, extra configuration, enablement, and last connection-test result. Prices, point prices, model IDs, endpoint IDs, model parameters, capabilities, and per-model concurrency quotas were compared before and after activation and remained identical. No billable generation request was made for verification.

The normal source change centralizes Ark supplier-code recognition in `VolcengineProviderCodes`. V155 creates the provider/configuration and moves the two existing model rows. The working tree contains other changes and a different migration lineage from production; production was updated from its exact original JAR using the focused compatibility tooling under `scripts/volcengine-xinji`, preserving every unrelated JAR entry and the existing frontend.

Validation: 73 targeted JUnit tests, 0 failures/errors; migration preservation test; original and Xinji video submission/cancellation coverage; offline verification of the exact candidate with JVM verification, native Ark routing, supplier identity/configuration, reservation metrics, and billing dimensions. Production service is active/running and `/actuator/health` returns `UP`.

- Original release: `/opt/antv/releases/20261026092633-5fdd9b2-asset-deletion`
- Active release: `/opt/antv/releases/20261026161515-xinji-provider`
- Private server-side configuration backup: `/opt/antv/backups/20261026161515-xinji-provider/before.private.json` (mode 0600; do not copy or print credentials)
- Credential-free activation receipt: `outputs/xinji-provider-activation-20261026.json`
- Test output: `outputs/xinji-tests.log`

Recovery guidance is in `scripts/volcengine-xinji/README.md`. Before reverting to a backend without Xinji code support, restore only the two model supplier assignments with the saved original IDs and account for any newly submitted tasks. Do not restore unrelated configuration or rewrite pricing/history.
