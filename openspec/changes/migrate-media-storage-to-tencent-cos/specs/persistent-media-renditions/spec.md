## ADDED Requirements

### Requirement: Every stored image has persistent WebP renditions
The system SHALL preserve each accepted image original and submit one idempotent Tencent Cloud Infinite processing task that stores one intelligent-compression display WebP. Thumbnail and normal-preview roles SHALL resolve to this same immutable object. Fixed processing parameters SHALL be server-controlled and outputs SHALL use an immutable `derived/` namespace that cannot retrigger the original-processing workflow.

#### Scenario: Image original is accepted
- **WHEN** a generated, uploaded, imported, reference, style, or cover image becomes a durable original
- **THEN** the system records its original metadata and submits the fixed persistent WebP jobs once
- **AND** records the resulting display object key and metadata when processing succeeds
- **AND** resolves any compatibility thumbnail field to the same display object key

#### Scenario: Derived output matches the workflow trigger
- **WHEN** Cloud Infinite writes an object below the asset version's `derived/` namespace
- **THEN** that object does not trigger another rendition workflow

#### Scenario: Required rendition fails
- **WHEN** Cloud Infinite cannot create a rendition required by the owning domain flow
- **THEN** the rendition is marked failed with retryable diagnostics
- **AND** the owning flow does not report a falsely complete published result

### Requirement: Cloud Infinite jobs and callbacks are authenticated and idempotent
The system SHALL persist each processing job identity, input version, requested output key, status, attempt, and provider evidence. Callback handling SHALL verify Tencent-origin authenticity and SHALL apply successful or failed terminal state at most once.

#### Scenario: Processing callback is delivered twice
- **WHEN** Cloud Infinite repeats a terminal callback for the same job and output
- **THEN** the second callback leaves persisted rendition state and owning business state unchanged

#### Scenario: Callback does not match the submitted asset version
- **WHEN** a callback identifies an unknown job, unexpected output key, or mismatched input version
- **THEN** the system rejects it without publishing a rendition

### Requirement: Video covers follow a deterministic source policy
The system SHALL create one persistent WebP cover per video version when its domain requires or requests a cover. AI-generated video SHALL reuse its bound first-frame image; episode composition SHALL prefer the first storyboard frame; other coverless video SHALL use a Cloud Infinite snapshot at one second with a bounded first-decodable-frame fallback for shorter media.

#### Scenario: AI video has a bound first frame
- **WHEN** an AI-generated video result is persisted with its authorized first-frame image
- **THEN** the system uses that image as the cover source and creates the persistent WebP cover without decoding the video

#### Scenario: Uploaded video has no cover image
- **WHEN** an uploaded or imported video requires a cover and has no suitable bound image
- **THEN** the system submits a persistent Cloud Infinite snapshot job and records its WebP cover output

#### Scenario: Episode composition disables optional cover generation
- **WHEN** an episode composition request explicitly sets `generateCover` to false and no domain contract requires a thumbnail
- **THEN** the system does not submit or synthesize a cover

### Requirement: Phase-one media processing is cost bounded
The system SHALL NOT automatically generate intelligent covers, arbitrary client-selected image sizes, multi-bitrate video ladders, or video transcodes for every source. A video compatibility rendition SHALL be created only when persisted source metadata proves the original is unsupported by target playback requirements or an authorized workflow explicitly requires it.

#### Scenario: Browser-compatible video is stored
- **WHEN** a video is already in the supported MP4 playback profile and needs only normal delivery
- **THEN** the system stores no redundant transcoded copy

#### Scenario: Client requests an arbitrary image size
- **WHEN** a caller supplies dimensions outside the fixed server rendition contract
- **THEN** the system rejects or ignores that request instead of creating a new persistent variant
