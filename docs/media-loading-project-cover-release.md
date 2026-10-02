# Media Loading and Project Cover Release

## Scope

This release changes project, public style, image/video/voice, shot/episode
composition, episode version, export, subtitle, visual-variant, and video
decomposition browser collections from arrays to bounded page envelopes. The
envelope is `{ data, current, pageSize, total }`; new endpoints default to page
1 with 20 records and cap requests at 100.

Project and video covers now resolve only to authorized persistent
`DISPLAY_IMAGE_SLIM` objects. Video playback grants are created only after a
user activates a player. Explicit original downloads and model-input identities
remain unchanged.

## Coordinated Deployment

1. Deploy the backend migration and application while the old frontend remains
   available only during the maintenance window. Do not expose the changed list
   contracts to old browser bundles.
2. Run the dry-run cover source inventory in
   `docs/project-cover-maintenance.md`. Resolve unsupported external sources or
   leave them visibly unavailable; do not enable original-image fallback.
3. Deploy the regenerated frontend bundle in the same release window. Purge the
   HTML/application bundle cache, while retaining immutable media cache objects.
4. Run the authorized project/style paging, cover-state, image visibility, and
   explicit video playback smoke checks before reopening traffic.
5. Run the bounded cover backfill/retry procedure from the maintenance runbook.
   Record counts only; signed URLs and source content must not enter logs.

Repository-wide consumer discovery is recorded in the OpenSpec endpoint matrix.
No repository consumer remains on a migrated array contract. The deployment
owner must confirm whether any separately deployed client consumes these APIs;
such a client must migrate to the page envelope before release.

## Rollback

Roll back backend and frontend together to the previous release. Do not reverse
or edit the applied Flyway migration, and do not delete newly registered
originals, display renditions, processing jobs, or delivery grants. The added
project cover columns are additive and can remain unused by the previous binary.

If only media processing is unhealthy, keep the new API contracts deployed,
stop bounded cover retries, and show the pending/failed placeholders. Do not
restore direct original image or eager video URLs as a workaround.

## Verification Evidence

The reusable browser harness is `scripts/verify-media-loading.mjs`. It writes
sanitized request counts and screenshots below `.temp/media-loading-qa/`.
Backend paging tests assert SQL limits, stable secondary ordering, authorized
totals, bounded representative results, and zero video delivery grants before
an explicit playback request.
