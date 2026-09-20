## Context

The existing video lifecycle already provides project authorization, model routing, execution attempts, point reservation, asynchronous polling, result storage, and accounting. Its Seedance transport is narrower than the provider contract: it builds one text item plus at most one first-frame image, has no provider cancellation, and accepts only the legacy duration set. The storyboard prompt document stores version 1 image-asset mentions, while the visible model selector and generation settings do not drive the submitted request.

The target Ark API accepts a common multimodal request for Seedance 2.0 mini, 2.0 Fast, 2.0 Standard, and 2.5. The variants share transport semantics but differ in output duration, resolution, and reference-media limits. Provider-facing URLs must remain accessible for asynchronous processing, and retries must not change when users later edit prompts or replace materials.

## Goals / Non-Goals

**Goals:**

- Route four independently configured and priced Seedance Models through one native Ark adapter.
- Compile visible rich-text media mentions into numbered provider prompt references and real Ark content items.
- Validate model-specific request and media constraints before point reservation or provider contact.
- Freeze effective parameters and resolved media references for deterministic retries and auditable billing.
- Inherit generation defaults from the project while allowing supported per-task overrides.
- Poll, persist provider metadata, download successful results, and cancel accepted external tasks.
- Remove version 1 prompt documents while preserving plain prompt text and historical execution data.

**Non-Goals:**

- Seedance 2.5 video-editing tasks.
- Base64 media submission or a new media-storage provider.
- Physical deletion of historical Models, tasks, prices, results, usage, or call logs.
- Automatic semantic conversion of version 1 mentions into version 2 media references.
- Redesigning the storyboard-card layout.

## Decisions

### Use four platform Models and one Ark adapter

The existing Fast, Standard, and 2.5 model rows retain their IDs; mini is added. Each Model owns its Endpoint ID, capability, constraint JSON, quota, price identity, and logs. Endpoint IDs are editable platform configuration and are never constants in the adapter. Model enablement rejects blank or placeholder Endpoint IDs. After configuration, all four Models are enabled and mini is the default.

This preserves platform routing and accounting boundaries. Separate adapters per variant were rejected because the HTTP lifecycle is identical and variant behavior is declarative.

### Keep model constraints in structured Model configuration

The backend reads duration, resolution, image, video, and audio limits from source-owned structured Model configuration and returns the supported options to the frontend. Backend validation remains authoritative.

Hard-coded conditionals across the frontend and service were rejected because they would drift when provider constraints change. Accepting all values and relying on Ark was rejected because invalid requests would reserve points and create avoidable provider calls.

### Compile version 2 prompt documents on the backend

Version 2 mention nodes contain stable source identity, media type, and display metadata but no authoritative URL. At task creation, the backend validates tenant/project ownership, resolves the current material, and assigns independent one-based labels (`图片N`, `视频N`, `音频N`) in first-mention order. Repeated mentions reuse one label and one content item.

Backend compilation prevents forged cross-project URLs and keeps batch, retry, and API clients consistent. Frontend compilation was rejected because it would make the browser authoritative for material ownership and request snapshots.

Version 1 is not supported. The migration clears version 1 `prompt_document_json` while preserving `video_prompt`. Conversion was rejected because version 1 lacks enough typed media identity to infer video and audio references safely.

### Persist normalized reference rows plus a request snapshot

`ai_video_task_reference` stores each unique resolved reference, its label, Ark role, source identity, frozen object-storage location, provider URL, metadata, and ordering. The task stores the compiled prompt, effective parameters, serialized request snapshot, and provider result metadata.

A JSON-only snapshot was rejected because ownership auditing, invalid-source diagnosis, and media-level inspection would be difficult. Re-resolving materials during retry was rejected because it would silently change provider input and billing evidence.

### Reuse object storage and send URLs

Existing suitable object-storage objects are reused. Local or private media is uploaded or copied before submission, and the generated URL must remain usable for the asynchronous provider lifetime. The system sends URLs only and never embeds Base64 data.

### Inherit project defaults and allow task overrides

Project aspect ratio remains authoritative. New project defaults are resolution (`720p`), generate audio (`true`), and watermark (`false`). Duration defaults to the storyboard plan. The compact storyboard settings control allows overrides, including intelligent duration `-1`. A model switch recomputes allowed values and falls back to `720p` when the prior resolution is invalid.

### Keep provider transport separate from compilation

The application service compiles and validates a frozen provider request. The Ark adapter performs POST, GET, and DELETE transport, supplies authorization and idempotency headers, maps status, and returns structured metadata. This keeps credentials and HTTP details in the adapter while preserving domain ownership in the video service.

### Make cancellation provider-aware and idempotent

When an external task exists, cancellation sends `DELETE /contents/generations/tasks/{id}`. HTTP 200 with an empty `Result` is success. Already-terminal or missing remote tasks converge to a stable local terminal state; a locally canceled task cannot re-enter polling.

## Data Flow

1. The frontend loads project defaults, enabled video Models, and Model constraints.
2. The user edits a version 2 prompt and selects project-owned image, video, or audio mentions.
3. The create request selects a real `modelId` and optional task parameter overrides.
4. The backend resolves effective project/task parameters and validates them against the Model.
5. The backend resolves each mention in tenant/project scope, validates trusted metadata, and prepares provider-accessible storage URLs.
6. The compiler produces the numbered text prompt and ordered typed content list.
7. The transaction persists the task and immutable reference/request snapshot and reserves points.
8. The existing execution lifecycle submits the Ark task and stores external identifiers.
9. Polling records non-terminal progress or terminal outcome. Success metadata and the returned video are stored under project ownership before settlement.
10. Cancellation contacts Ark when necessary and fences later polls.

## Model Constraint Matrix

| Model | Duration | Resolution | Images | Videos | Audio |
| --- | --- | --- | --- | --- | --- |
| 2.0 mini | 4-15 or -1 | 480p, 720p | 1-9 | <=3, total <=15s | <=3, total <=15s |
| 2.0 Fast | 4-15 or -1 | 480p, 720p | 1-9 | <=3, total <=15s | <=3, total <=15s |
| 2.0 Standard | 4-15 or -1 | 480p, 720p, 1080p, 4k | 1-9 | <=3, total <=15s | <=3, total <=15s |
| 2.5 | 4-30 or -1 | 480p, 720p, 1080p | 1-30 | <=10, total <=30s | <=10, total <=30s |

Image validation covers format, 0.4-2.5 aspect ratio, 300-6000 pixel dimensions, and less than 30 MB. Video validation covers mp4/mov, 0.4-2.5 aspect ratio, 300-6000 pixel dimensions, 407696-8295044 pixels, at most 200 MB, 24-60 FPS, and model duration totals. Audio validation covers wav/mp3, at most 15 MB, and model duration totals.

## Risks / Trade-offs

- [Provider cannot fetch an expiring URL] -> Use object-storage URLs whose validity exceeds execution timeout and test access before submission.
- [Stored metadata is missing for legacy media] -> Probe or backfill metadata before accepting the reference; fail before provider contact when authoritative validation is impossible.
- [Large reference sets slow task creation] -> Deduplicate before storage preparation and bound work by Model limits.
- [Version 1 cleanup removes editable tags] -> Preserve plain `video_prompt`, document the breaking migration, and require explicit rebinding or regeneration.
- [Remote cancellation races with completion] -> Reconcile terminal provider state idempotently and fence local state transitions.
- [Endpoint IDs differ across deployments] -> Keep them in platform Model configuration and fail closed until an authorized administrator configures and enables each Model.
- [Provider error schemas vary] -> Parse top-level and nested error code/message fields and retain only a bounded sanitized diagnostic summary.

## Migration Plan

1. Deploy an additive migration that adds mini, updates existing Seedance constraint configuration, adds project/task/reference storage, and clears version 1 prompt JSON without deleting plain prompts or history.
2. Deploy backend APIs and runtime support while Seedance Models remain fail-closed when Endpoint IDs or Provider credentials are unavailable.
3. Configure the four Endpoint IDs through platform Model management, validate Provider credentials, enable the four Models, and set mini as default.
4. Deploy the frontend that writes version 2 documents and submits real Model IDs and parameters.
5. Run schema, backend, frontend, lint, build, and real Ark smoke verification.

Rollback disables the four Models and restores the prior application version. Additive columns and reference rows remain inert. Cleared version 1 JSON is intentionally not reconstructed; database backup restoration is the only rollback for that destructive cleanup.

## Open Questions

None. The provider failure body has no supplied example, so compatible top-level and nested error parsing is an explicit design decision rather than an unresolved dependency.
