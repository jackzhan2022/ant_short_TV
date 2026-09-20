# Seedance Video Generation Runbook

## Scope

This runbook covers Seedance 2.0 mini, 2.0 Fast, 2.0 Standard, and 2.5 multimodal video generation through Volcengine Ark. It does not cover Seedance 2.5 video-editing tasks.

## Platform configuration

Only platform administrators may configure the Provider and Models. Configure the `VOLCENGINE_ARK` Provider with:

- Base URL: `https://ark.cn-beijing.volces.com/api/v3`
- API key: a production Ark key stored through platform Provider management
- Provider, Provider configuration, and `VIDEO_GENERATION` capabilities enabled

Set each Model's `modelCode` to its deployment-specific Ark Endpoint ID before enabling it:

| Model code | Endpoint ID |
| --- | --- |
| `SEEDANCE_2_0_MINI` | `ep-20260919233508-9rxwc` |
| `SEEDANCE_2_0_FAST` | `ep-20260919234255-n6z7x` |
| `SEEDANCE_2_0_STANDARD` | `ep-20260919234226-qp8p2` |
| `SEEDANCE_2_5` | `ep-20260919234108-rqlv9` |

Enable the Provider first, then each Model and its capability, and finally set `SEEDANCE_2_0_MINI` as the default video Model. Blank values and `__SEEDANCE_*_ENDPOINT_ID__` placeholders must remain disabled. Confirm the tenant project Model-options response includes safe constraints but does not include Endpoint IDs, Provider credentials, or signed media URL queries.

## Runtime constraints

| Model | Output duration | Output resolutions | Images | Videos | Audio |
| --- | --- | --- | --- | --- | --- |
| 2.0 mini | 4-15 seconds or `-1` | 480p, 720p | 1-9 | up to 3, total 15 seconds | up to 3, total 15 seconds |
| 2.0 Fast | 4-15 seconds or `-1` | 480p, 720p | 1-9 | up to 3, total 15 seconds | up to 3, total 15 seconds |
| 2.0 Standard | 4-15 seconds or `-1` | 480p, 720p, 1080p, 4k | 1-9 | up to 3, total 15 seconds | up to 3, total 15 seconds |
| 2.5 | 4-30 seconds or `-1` | 480p, 720p, 1080p | 1-30 | up to 10, total 30 seconds | up to 10, total 30 seconds |

Images must use a supported image format, be under 30 MB, have dimensions from 300 to 6000 pixels, and have an aspect ratio from 0.4 to 2.5. Videos must be mp4 or mov, be no larger than 200 MB, use 24-60 FPS, have valid dimensions/aspect ratio, and contain 407696-8295044 pixels. Audio must be wav or mp3 and no larger than 15 MB. The backend is authoritative and rejects invalid references before point reservation or Provider contact.

## Deployment and migration verification

Back up the database before applying V119. The migration is additive except for version 1 prompt-document cleanup. It preserves plain `storyboard.video_prompt` but clears version 1 `prompt_document_json`; this cleanup is intentional and cannot be reconstructed from the migrated database.

Run the schema verification before deployment:

```powershell
cd backend
mvn "-Dtest=SeedanceMultimodalMigrationTest,AiMigrationSnapshotRehearsalTest,LegacyAiConfigurationCleanupMigrationTest" test
```

After migration, verify:

```sql
select code, id, model_code, status, is_default
from ai_model
where code in ('SEEDANCE_2_0_MINI', 'SEEDANCE_2_0_FAST', 'SEEDANCE_2_0_STANDARD', 'SEEDANCE_2_5');

select count(*) as remaining_v1_documents
from storyboard
where prompt_document_json like '%"version":1%'
   or prompt_document_json like '%"version": 1%'
   or prompt_document_json like '%"version" : 1%';
```

Compare pre-deployment and post-deployment counts and sampled IDs for `ai_model`, `ai_model_price_version`, `ai_model_point_price_version`, `ai_execution_task`, `ai_call_log`, `ai_video_task`, `ai_video_result`, `ai_usage_line`, and `team_point_ledger`. Historical rows and the three existing Seedance Model IDs must be unchanged. Only version 1 prompt JSON may be cleared; corresponding `video_prompt` values must remain unchanged.

## Ark smoke test

Use a dedicated tenant/project and non-sensitive reference media. Do not print Authorization headers, decrypted API keys, or signed URL query strings.

1. Submit one task for each enabled Model with image, video, and audio mentions. Confirm the frozen request contains the real Model ID, compiled `图片N`/`视频N`/`音频N` text, ordered typed content, project ratio, effective duration/resolution, generated-audio flag, and watermark flag.
2. Poll until success. Confirm the returned video is copied into project-owned storage and actual duration, resolution, ratio, seed, FPS, service tier, generated-audio flag, and usage tokens are persisted when Ark returns them.
3. Submit a second task and cancel it after Ark accepts it. Confirm Ark DELETE returns HTTP 200, the local task becomes `CANCELED`, its reservation is released, and later polling cannot resume it.
4. Submit a deliberately invalid media reference. Confirm validation names the material and no execution, reservation, or Provider call is created.
5. Exercise an Ark failure response. Confirm the stored diagnostic is bounded and sanitized.
6. Force an infrastructure retry, then edit the storyboard/material. Confirm the retry reuses the original Endpoint ID, parameters, compiled prompt, reference rows, idempotency key, and request snapshot.

## Incident recovery

- Configuration rejection: keep the Model disabled, correct its Endpoint ID or Provider credential, then enable it again. Never bypass placeholder validation.
- Provider fetch failure: verify the frozen object-storage object still exists and its Provider URL remains accessible for the full asynchronous execution window. Do not replace the reference on an existing task; create an intentional regeneration after fixing storage.
- Stuck execution: inspect the execution attempt, claim expiry, external task ID, and Ark state before retrying. Infrastructure retries must reuse the frozen snapshot.
- Cancellation race: retain the first authoritative terminal state and its Provider linkage. Do not manually move a canceled task back to a pollable state.
- Accounting anomaly: preserve task, attempt, reservation, usage, cost, and ledger records. Reconcile with the frozen Model and price versions before any compensating entry.
- Cleared version 1 prompt: restore from the pre-deployment database backup only when the legacy JSON itself is required. Otherwise retain the plain prompt and rebind materials in the workbench to create version 2.

## Rollback

Stop new video submissions and disable all four Seedance Models before application rollback. Allow accepted tasks to reach a terminal state or cancel them through the normal API, then deploy the previous compatible application.

Keep V119's additive columns and `ai_video_task_reference` rows in place; older code ignores them. Do not delete historical tasks, results, prices, accounting records, or call logs. Version 1 prompt JSON is not recoverable by rolling back application code or SQL; restoring the pre-deployment database backup is the only rollback for that destructive cleanup and will also discard later writes.
