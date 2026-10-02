# Project cover migration and maintenance

Apply `V129__project_cover_display_state.sql` together with the backend and frontend
page contract update. No production data changes or remote imports are executed by
this implementation. Back up the database before an operator starts maintenance.

## Read-only source inventory

Use the following query against a read replica or approved maintenance connection.
It returns counts only, never Base64, signed URLs, prompts, or source strings.
Replace the configured CDN prefix with the deployment's actual CDN domain.

```sql
select source_type, count(*) as source_count from (
  select case
    when cover_url is null or cover_url = '' then 'MISSING'
    when cover_url like 'data:image/%;base64,%' then 'DATA_URL'
    when cover_url like '/api/style-library/images/%' then 'PUBLIC_STYLE_API'
    when cover_url like '/api/projects/%/ai-image-results/%' then 'AI_RESULT_API'
    when cover_url like 'materials/%' or cover_url like '/materials/%' then 'PLATFORM_KEY'
    when cover_url like 'https://antvcdn.aixmax.cn/materials/%' then 'CONFIGURED_CDN'
    when cover_url like 'https://%' or cover_url like 'http://%' then 'EXTERNAL_URL'
    else 'UNSUPPORTED'
  end as source_type from project where deleted_at is null
) source_inventory group by source_type;

select coalesce(cover_status, 'UNPROCESSED') as status, count(*) as project_count
from project where deleted_at is null group by coalesce(cover_status, 'UNPROCESSED');
```

Record only aggregate counts and project IDs requiring attention. `cover_error`
contains a source-free failure classification. Raw `cover_url` is durable internal
source metadata and must not be exported into logs or reports.

## Backfill And Retry

Backfill is a bounded, authorized call to `GET /api/projects/{id}/cover/status`, one
project at a time. Use a current authenticated session with project view access.
It derives the tenant from the project ID, so no tenant header is required.
Select explicit project IDs from the inventory; do not browse all projects to
trigger work. Limit each maintenance batch to 100 IDs, and inspect the aggregate
READY/PENDING/FAILED counts before proceeding.

Each request checks source ownership and resolves a stable SHA-256 version.
Public style and owned platform results with READY imageSlim objects reuse that
object. Historical images are bounded to 20 MiB and 100 million pixels. Arbitrary
external URLs are retained with FAILED status; ordinary reads never fetch them.
An operator may import such a source through the existing controlled upload
session workflow, then bind it with
`POST /api/projects/{id}/cover/upload {"sessionToken":"..."}`. The completed session
must belong to the current user and tenant; project uploads must match the project.

If status is PENDING, wait for the existing authenticated CI callback to finish,
then re-read status. Repeated requests reuse the source version and atomically
claim processing once. List GETs never perform backfill or submit processing.
The status response is `{status,retryable,coverUrl}` inside `ApiResponse.data`.

For FAILED rows, an editor may invoke `POST /api/projects/{id}/cover/retry` once,
then poll status with the same bounded cadence used by the UI (5 seconds, at most
12 polls). READY originals are reused when retrying the existing display job.
Unsupported sources require an explicit replacement upload; repeating retry will
retain the diagnostic. Inspect CI job state and object registry state before
manually recovering an interrupted process claim. Never delete existing media
versions to force a retry.

If an operator has confirmed that the process holding a PENDING claim stopped and
there is no active CI submission, the following targeted recovery marks that exact
source version FAILED. Replace the ID/version parameters from the approved batch,
then invoke the editor-only retry endpoint. The version comparison makes repeating
this update harmless and protects a replacement source. This is an operator action,
not a scheduled blanket update, and has not been run against production.

```sql
update project p set cover_status = 'FAILED',
  cover_error = 'Interrupted cover claim confirmed by operator'
where p.id = :project_id and p.cover_version = :expected_version
  and p.deleted_at is null and p.cover_status = 'PENDING'
  and not exists (
    select 1 from media_object m where m.tenant_id = p.tenant_id
      and m.project_id = p.id and m.asset_type = 'PROJECT_COVER'
      and m.asset_id = p.id and m.version_id = p.cover_version
      and m.rendition_type = 'DISPLAY_IMAGE_SLIM' and m.status in ('PENDING', 'READY')
  );
```

The browser display endpoint returns an uncached redirect only to READY
`DISPLAY_IMAGE_SLIM`; MISSING/PENDING/FAILED return 204 with `Cache-Control: no-store`.
Explicit source changes create a new version. Database writes check that version,
so a finishing old process cannot canonicalize or mark a newer source READY.
Callback jobs update only their immutable registry identity.

## Editing And Rollback

Submitting the returned `/api/projects/{id}/cover` preserves the original source.
Omitted or null coverUrl preserves it on name-only edits; `clearCover:true` or an
empty coverUrl clears it. New Data URLs remain a bounded compatibility path;
controlled upload sessions are preferred for new files.

Rollback frontend and backend page contracts together. Keep the forward schema,
source versions, registered originals, and display objects. Do not reverse Flyway
or replace model reference inputs with UI display URLs. Current project access is
checked on every cover/status request, including after membership removal.
