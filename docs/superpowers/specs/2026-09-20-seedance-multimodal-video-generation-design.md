# Seedance Multimodal Video Generation Design

## Goal

Replace the current single-first-frame Seedance request construction with one durable multimodal video-generation path for Seedance 2.0 mini, Seedance 2.0 Fast, Seedance 2.0 Standard, and Seedance 2.5. The storyboard UI keeps its current visual structure while rich-text material mentions are compiled into Ark text references and real media inputs at task creation time.

## Scope

This change covers platform model definitions, project video defaults, rich-text material selection, immutable video-task request snapshots, Volcengine Ark submission, polling, cancellation, result metadata, validation, and focused UI changes.

Seedance 2.5 video-editing tasks are out of scope. This design covers multimodal reference-to-video generation only.

## Platform Models

One `VOLCENGINE_ARK` Provider owns the Base URL and API key. Four independently selectable Models share the Ark transport adapter:

| Stable model code | Display name | Initial Endpoint ID |
| --- | --- | --- |
| `SEEDANCE_2_0_MINI` | Seedance 2.0 mini | `ep-20260919233508-9rxwc` |
| `SEEDANCE_2_0_FAST` | Seedance 2.0 Fast | `ep-20260919234255-n6z7x` |
| `SEEDANCE_2_0_STANDARD` | Seedance 2.0 Standard | `ep-20260919234226-qp8p2` |
| `SEEDANCE_2_5` | Seedance 2.5 | `ep-20260919234108-rqlv9` |

Endpoint IDs are platform configuration, not adapter constants. Platform administrators save the Endpoint ID in each Model's real model-code field. A Model cannot be enabled while its Endpoint ID is blank or still a placeholder. After all four are configured, all four are enabled and Seedance 2.0 mini is the default video Model.

Each Model retains an independent ID, price identity, quota, call-log identity, status, and `VIDEO_GENERATION` capability. Existing Fast, Standard, and 2.5 model rows are updated in place so historical task, pricing, and log references remain valid. Mini is added as a new model.

## Model Constraints

Model constraints live in structured Model configuration and are enforced by the backend. They are also returned to the frontend so controls do not duplicate provider rules.

| Model | Output duration | Output resolution | Images | Reference videos | Reference audio |
| --- | --- | --- | --- | --- | --- |
| 2.0 mini | `4-15` or `-1` | 480p, 720p | 1-9 | Up to 3, total <= 15s | Up to 3, total <= 15s |
| 2.0 Fast | `4-15` or `-1` | 480p, 720p | 1-9 | Up to 3, total <= 15s | Up to 3, total <= 15s |
| 2.0 Standard | `4-15` or `-1` | 480p, 720p, 1080p, 4k | 1-9 | Up to 3, total <= 15s | Up to 3, total <= 15s |
| 2.5 | `4-30` or `-1` | 480p, 720p, 1080p | 1-30 | Up to 10, total <= 30s | Up to 10, total <= 30s |

`duration = -1` means intelligent duration and does not guarantee a specific output duration. The default is the storyboard planned duration; a user may override it or select intelligent duration.

### Image validation

- Formats: jpeg, png, webp, bmp, tiff, gif, heic, and heif.
- Width-to-height ratio: `0.4-2.5`.
- Width and height: each `300-6000` pixels.
- Size: less than 30 MB per image.
- Full request body: less than 64 MB. The application sends URLs and never embeds Base64 media.

### Video validation

- Formats: mp4 and mov.
- Resolution classes: 480p, 720p, 1080p, and 4k.
- Width-to-height ratio: `0.4-2.5`.
- Width and height: each `300-6000` pixels.
- Total pixels: `407696-8295044`.
- Size: no more than 200 MB per video.
- Frame rate: `24-60` FPS.
- Seedance 2.0 series: each video `2-15s`, at most 3 videos, total duration no more than 15s.
- Seedance 2.5: each video `2-30s`, at most 10 videos, total duration no more than 30s.

### Audio validation

- Formats: wav and mp3.
- Size: no more than 15 MB per audio file.
- Seedance 2.0 series: each audio file `2-15s`, at most 3 files, total duration no more than 15s.
- Seedance 2.5: each audio file `2-30s`, at most 10 files, total duration no more than 30s.

The frontend provides immediate validation feedback. The backend repeats all security-sensitive and provider-contract validation before reserving points or contacting Ark.

## Project Defaults And Per-Task Overrides

The project remains the source of default video settings:

- `ratio` comes from the existing project `aspectRatio`.
- A new project resolution setting defaults to `720p`.
- A new generate-audio setting defaults to `true`.
- A new watermark setting defaults to `false`.
- Duration defaults to the storyboard planned duration.

The existing compact parameter control on each storyboard allows per-task overrides for duration, resolution, generate audio, and watermark. Switching Models recomputes allowed values. An unsupported resolution falls back to `720p` with a visible notice. Batch generation freezes the effective settings separately for each storyboard.

## Rich-Text Material References

The storyboard card and prompt editor keep their current visual layout. The existing `+` and `@` controls become functional project-material selectors grouped by image, video, and audio.

Only prompt-document version 2 is supported after this change. A version 2 mention stores stable source identifiers and display data, not a temporary URL. Image mentions may refer to character, scene, prop, visual-variant, first-frame, or other project-owned image sources. Video and audio mentions refer to project-owned material records.

The UI renders each mention as `@material name`. The provider never receives that display label directly. At task creation the backend walks nodes in document order and assigns independent, one-based labels by media type:

- images become `图片1`, `图片2`, and so on;
- videos become `视频1`, `视频2`, and so on;
- audio files become `音频1`, `音频2`, and so on.

Repeated mentions of the same source reuse the same label and generate one Ark content item. Text nodes remain unchanged. Mention nodes in the compiled provider prompt are replaced by their assigned labels.

Version 1 prompt documents are deliberately not supported or converted. The migration clears every stored version 1 `prompt_document_json` value while preserving the plain `video_prompt`. Those storyboards must bind materials again or be regenerated before they can create a version 2 multimodal task. No runtime version 1 compatibility path remains.

## Material Resolution And Upload

At task creation the backend:

1. Loads the storyboard and version 2 prompt document in the active tenant and project scope.
2. Resolves each mention to a current project-owned source and rejects missing, deleted, cross-tenant, or cross-project sources.
3. Reads trusted media metadata and validates it against the selected Model.
4. Reuses an object-storage object when it is already suitable for Ark access.
5. Uploads or copies local/private media to object storage when necessary and produces a URL whose lifetime covers provider execution.
6. Compiles the provider prompt and ordered content items.
7. Persists an immutable request and reference snapshot.
8. Reserves points and submits to Ark.

Material resolution or validation failure occurs before provider contact. URLs are never accepted from the browser as authoritative project ownership evidence.

## Durable Task Snapshot

`ai_video_task` gains the effective Model and generation parameters, compiled prompt, request snapshot, and provider result metadata needed for deterministic retries. A new `ai_video_task_reference` table stores one row per unique compiled reference:

- tenant, project, task, and storyboard IDs;
- media type and one-based media index;
- Ark role (`reference_image`, `reference_video`, or `reference_audio`);
- source type, source ID, and optional visual-variant ID;
- display name and compiled label;
- frozen object-storage path and provider-accessible URL;
- format, byte size, width, height, duration, FPS, and ordering;
- creation timestamp.

Retries use the frozen task and reference snapshot. They do not re-read a changed prompt document or silently switch to a newer material variant.

## Ark Request Contract

Submission uses:

```http
POST /api/v3/contents/generations/tasks
Authorization: Bearer <platform provider API key>
Content-Type: application/json
Idempotency-Key: <attempt idempotency key>
```

The request contains:

- the Model's configured Endpoint ID in `model`;
- a text content item with the compiled prompt;
- ordered `image_url`, `video_url`, and `audio_url` items with their reference roles;
- effective `generate_audio`, `ratio`, `duration`, `resolution`, and `watermark` values.

Content is ordered as text first, then unique media references in their first-mention order. The adapter is responsible only for Ark transport and response mapping; prompt compilation, ownership checks, and validation remain in the video application service.

## Polling, Completion, And Cancellation

Polling uses `GET /api/v3/contents/generations/tasks/{id}`. Provider states map to the existing application lifecycle: pending states remain pending, active states become generating, success becomes succeeded, and failed, canceled, or expired states become terminal failures or cancellation as appropriate. Unknown non-terminal states continue polling until the application timeout.

On success, the service reads `content.video_url`, downloads the video into project-owned object storage, and records provider metadata including actual duration, resolution, ratio, seed, FPS, service tier, audio-generation flag, and usage tokens. Existing call-log, point settlement, and result-binding behavior remains in force.

Cancellation uses `DELETE /api/v3/contents/generations/tasks/{id}` when an external task exists. HTTP 200 with an empty `Result` object is success. Repeated cancellation and a provider task that is already terminal or absent are handled idempotently. Once locally canceled, a task cannot resume provider polling.

Non-2xx responses parse both top-level `code` and `message` and nested `error.code` and `error.message`. The application stores a bounded, sanitized diagnostic summary. It does not expose credentials, signed URL query strings, or full raw payloads to end users.

## Database Migration

An additive migration, rather than editing V56, will:

- add the mini Model and its `VIDEO_GENERATION` capability;
- update the three existing Seedance model definitions in place;
- store the model-specific constraint configuration;
- keep Endpoint IDs platform-managed and reject enabling unresolved placeholders;
- make mini the default after configuration;
- add project video-default columns;
- add task snapshot and result-metadata columns;
- create `ai_video_task_reference` with tenant/project indexes and task ownership constraints;
- clear all version 1 storyboard prompt documents while retaining plain prompts.

The migration does not physically delete historical Models, tasks, results, pricing versions, usage records, or call logs.

## Frontend Behavior

- The Model selector loads enabled project video models and submits the selected `modelId`; no display-only hard-coded Model remains.
- The project settings surface resolution, generate-audio, and watermark defaults.
- The compact per-storyboard settings control allows supported overrides without changing the card layout.
- The material selector inserts version 2 mentions while preserving the current rich-text appearance.
- Validation errors identify the exact material and violated constraint.
- Batch generation reports each storyboard independently; one invalid storyboard does not prevent valid siblings from submitting.

## Testing And Verification

Backend tests cover:

- four model definitions, routing, configuration guards, and mini default selection;
- version 2 prompt compilation, independent numbering, stable ordering, and duplicate-reference reuse;
- rejection of version 1 documents and verification that migration cleanup preserves plain prompts;
- tenant/project ownership and deleted-material rejection;
- every duration, resolution, image, video, and audio boundary;
- exact Ark submission JSON for all content types and generation parameters;
- pending, success, failure, cancellation, unknown status, and provider error parsing;
- success metadata capture and project-owned video storage;
- deterministic retry from frozen snapshots;
- remote DELETE cancellation and local polling protection;
- migration preservation of old task, pricing, and call-log references.

Frontend tests cover:

- real Model loading and `modelId` submission;
- project defaults and per-task overrides;
- automatic `720p` fallback after an incompatible Model switch;
- material selection and version 2 mention editing;
- visible validation feedback;
- batch partial failure behavior.

Completion requires focused backend and frontend tests, full frontend lint and type checking, backend test completion, and a production build. A real Ark smoke test must submit, poll, download, and cancel tasks using platform-configured credentials without logging secrets.

## Acceptance Criteria

- Users can select any configured Seedance Model and the selected Model's Endpoint ID is used.
- The visible rich-text prompt remains human-readable while Ark receives numbered text references and real media URLs.
- All provider constraints described in this design are enforced before provider contact.
- The provider request includes multimodal references, audio generation, ratio, duration, resolution, and watermark.
- Retries are deterministic and cancellation reaches both Ark and local task state.
- Version 1 rich-text documents are removed and no version 1 runtime compatibility remains.
- Existing historical task, result, pricing, accounting, and log relationships remain intact.
