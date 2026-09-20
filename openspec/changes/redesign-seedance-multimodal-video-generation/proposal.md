## Why

The current Seedance integration only submits a text prompt and one first-frame image, while the four target Seedance variants use a shared multimodal reference contract with ordered image, video, and audio inputs. The workbench also shows a hard-coded model and generation settings, so it cannot reliably select, validate, retry, or cancel real model-specific tasks.

## What Changes

- Redesign the Volcengine Ark video path for Seedance 2.0 mini, Seedance 2.0 Fast, Seedance 2.0 Standard, and Seedance 2.5 with independently configured Endpoint IDs and model constraints.
- Compile storyboard rich-text material mentions into numbered `图片N` / `视频N` / `音频N` prompt references and ordered Ark multimodal content items.
- Persist immutable task parameters and resolved media-reference snapshots so retries use the original request inputs.
- Validate model-specific duration, resolution, image, video, and audio constraints before provider contact.
- Add project video defaults for resolution, generated audio, and watermark behavior while retaining the existing project aspect ratio.
- Replace the display-only model selector and fixed task parameters with real project models, inherited defaults, and per-task overrides without changing the storyboard-card layout.
- Extend Ark polling result metadata and send provider-native DELETE cancellation for accepted external tasks.
- **BREAKING** Remove prompt-document version 1 data and runtime compatibility. The migration clears version 1 rich-text JSON while retaining plain `video_prompt`; affected storyboards must bind materials again or be regenerated.
- Preserve historical model IDs, video tasks, pricing, accounting, results, and call logs while updating the three existing model definitions and adding Seedance 2.0 mini.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `volcengine-seedance-video-generation`: Expand model routing and native Ark execution from single-frame input to four-model multimodal submission, validation, deterministic snapshots, metadata capture, and remote cancellation.
- `platform-ai-configuration`: Make Seedance Endpoint IDs platform-managed model configuration and expose model constraint metadata for authorized configuration and routing.
- `short-drama-storyboard-agent`: Replace version 1 prompt documents with version 2 media mentions that compile to typed, numbered provider references.
- `script-project-creation`: Persist project-level video resolution, generated-audio, and watermark defaults alongside the existing aspect ratio.
- `production-workbench-metadata`: Display and use authoritative project video defaults instead of fixed resolution and generation literals.

## Impact

- Backend video task requests, entities, mappers, services, schedulers, Ark adapter, project APIs, object-storage access, model configuration, validation, billing evidence, and database migrations.
- Frontend project creation/settings, project model loading, storyboard prompt editor, media selector, generation parameter controls, batch generation, task status, and service contracts.
- Existing version 1 `prompt_document_json` values are intentionally cleared; plain prompts and all historical execution/accounting records remain.
- No new external dependency is required; the existing Ark HTTP API and object-storage layer remain the integration boundaries.
