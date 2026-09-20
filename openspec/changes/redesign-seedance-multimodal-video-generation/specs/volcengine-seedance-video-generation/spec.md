## MODIFIED Requirements

### Requirement: Seedance variants are independently routable video-generation models
The system SHALL define Seedance 2.0 mini, Seedance 2.0 Fast, Seedance 2.0 Standard, and Seedance 2.5 as separate platform Models under one Volcengine Ark Provider. Each Model SHALL support `VIDEO_GENERATION` and retain an independent stable code, Endpoint ID, constraint profile, pricing identity, quota identity, and call-log identity. Seedance 2.0 mini SHALL be the default enabled video Model after its Endpoint ID is configured.

#### Scenario: A selected Seedance variant starts a video task
- **WHEN** a project submits an authorized video-generation task with an enabled Seedance 2.0 mini, Seedance 2.0 Fast, Seedance 2.0 Standard, or Seedance 2.5 model ID
- **THEN** the system resolves that exact Model and uses its configured Ark Endpoint ID and constraint profile for provider submission

#### Scenario: No explicit video model is selected
- **WHEN** a project has no explicit video Model override and Seedance 2.0 mini is configured and enabled as the platform default
- **THEN** the system resolves Seedance 2.0 mini and persists it as the task's requested and resolved Model

#### Scenario: Seedance variants are priced independently
- **WHEN** a platform administrator assigns prices to two different Seedance variants
- **THEN** each variant's price versions apply only to tasks resolved to that Model

### Requirement: Seedance uses native Ark asynchronous task operations
The system SHALL submit Seedance video-generation requests to Volcengine Ark as provider-native asynchronous tasks, SHALL poll the returned external task identifier until a terminal provider state is observed, and SHALL request provider-native cancellation for a cancelable accepted task.

#### Scenario: Ark accepts a generation task
- **WHEN** the Seedance adapter receives a valid frozen video-generation request with configured credentials and a valid Endpoint ID
- **THEN** it sends the Ark request, records the provider request ID and external task ID, and leaves the application task non-terminal for polling

#### Scenario: Ark completes a generation task
- **WHEN** polling returns a successful terminal Ark task with `content.video_url`
- **THEN** the system records provider completion and provider metadata and passes the result URL to the existing project-owned result-storage flow

#### Scenario: Ark reports a failed generation task
- **WHEN** polling returns a failed, expired, or cancelled terminal Ark task
- **THEN** the system records the corresponding terminal application state with normalized diagnostics and retains the provider request/task linkage in execution and call-log records

#### Scenario: User cancels an accepted Ark task
- **WHEN** an authorized user cancels a non-terminal task that has an external Ark task ID
- **THEN** the system sends `DELETE /contents/generations/tasks/{id}` and converges local state to `CANCELED` without allowing later polling to resume it

#### Scenario: Cancellation is repeated or races with completion
- **WHEN** cancellation is repeated or Ark reports that the task is already terminal or absent
- **THEN** the system handles the response idempotently and preserves one authoritative local terminal state

### Requirement: Seedance request input maps from a frozen multimodal video task
The system SHALL construct Ark requests from the resolved Model and an immutable task snapshot containing the compiled prompt, effective generation parameters, and ordered typed media references. The request SHALL include the selected Endpoint ID, `generate_audio`, ratio, duration, resolution, watermark, and `image_url`, `video_url`, and `audio_url` content items with matching reference roles.

#### Scenario: Multimodal task is submitted
- **WHEN** a valid task snapshot contains image, video, and audio references
- **THEN** Ark receives one text content item followed by the unique typed media items in first-mention order with `reference_image`, `reference_video`, and `reference_audio` roles

#### Scenario: Repeated material is referenced
- **WHEN** the same source material appears more than once in the prompt document
- **THEN** the compiled prompt reuses the same typed number and the Ark request contains one content item for that source

#### Scenario: Technical retry occurs after source material changes
- **WHEN** a provider submission or polling attempt is retried after the prompt or source material has changed
- **THEN** the retry uses the original frozen Model, parameters, compiled prompt, and material-reference snapshot

### Requirement: Unconfigured Seedance definitions fail closed
The four Seedance Models SHALL reject routing until their platform Model configuration contains a non-placeholder Endpoint ID and the Model, Capability, Provider, and Provider configuration are enabled. The adapter SHALL reject a blank or placeholder Endpoint ID before contacting Ark.

#### Scenario: User selects a disabled Seedance model
- **WHEN** a project requests video generation with a disabled Seedance Model
- **THEN** model routing rejects the request before provider contact

#### Scenario: Placeholder Endpoint ID reaches the adapter
- **WHEN** an enabled Seedance Model still contains an unresolved Endpoint-ID placeholder
- **THEN** the adapter returns a normalized configuration error without sending an HTTP request

## ADDED Requirements

### Requirement: Seedance media references compile from version 2 prompt documents
The system SHALL compile each version 2 media Mention into a numbered provider-text reference and a tenant- and project-authorized media snapshot. Images, videos, and audio SHALL use independent one-based numbering and SHALL display their human-readable material names in the editor.

#### Scenario: Mixed media prompt is compiled
- **WHEN** a version 2 prompt contains image, video, and audio Mentions
- **THEN** the provider text replaces them with `图片N`, `视频N`, and `音频N` according to first occurrence within each media type
- **AND** the visible saved document retains the human-readable Mention display names

#### Scenario: Mention source is not authorized
- **WHEN** a Mention resolves to a missing, deleted, cross-tenant, or cross-project source
- **THEN** task creation fails before point reservation and provider contact

### Requirement: Seedance validates model-specific output and reference constraints
The system MUST validate effective duration, resolution, image metadata, video metadata, audio metadata, per-type counts, and total reference duration against the selected Model before provider contact.

#### Scenario: Seedance 2.0 request uses supported values
- **WHEN** a Seedance 2.0 request uses duration `4-15` or `-1`, a Model-supported resolution, 1-9 valid images, no more than 3 valid videos totaling at most 15 seconds, and no more than 3 valid audio files totaling at most 15 seconds
- **THEN** model-specific validation accepts the request

#### Scenario: Seedance 2.5 request uses supported values
- **WHEN** a Seedance 2.5 request uses duration `4-30` or `-1`, a supported resolution, 1-30 valid images, no more than 10 valid videos totaling at most 30 seconds, and no more than 10 valid audio files totaling at most 30 seconds
- **THEN** model-specific validation accepts the request

#### Scenario: Media violates a provider constraint
- **WHEN** a reference violates its format, byte-size, dimension, aspect-ratio, pixel-count, FPS, duration, count, or total-duration constraint
- **THEN** the system identifies the invalid material and rejects the task before point reservation and provider contact

### Requirement: Seedance completion retains provider result metadata
The system SHALL persist the bounded provider result metadata needed to audit the generated output and usage, including actual duration, resolution, ratio, seed, FPS, service tier, generated-audio flag, and usage token counts when present.

#### Scenario: Successful response includes output metadata
- **WHEN** Ark returns a successful task with video URL, output fields, and usage
- **THEN** the system stores the metadata, downloads the video into project-owned storage, and settles the task using the existing execution and accounting lifecycle

#### Scenario: Provider failure shape varies
- **WHEN** Ark returns a non-success HTTP response or terminal failure with top-level or nested error fields
- **THEN** the system records a bounded sanitized code and message without persisting credentials or signed URL query strings
