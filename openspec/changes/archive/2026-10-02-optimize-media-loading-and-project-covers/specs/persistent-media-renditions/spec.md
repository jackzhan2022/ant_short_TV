## ADDED Requirements

### Requirement: Project covers expose compressed display resources
The system SHALL preserve a stable project cover source/version identity and resolve project-list and script-page cover rendering exclusively to its READY persistent original-resolution imageSlim display object. Project responses SHALL expose a stable cover display reference and processing state without embedding original Base64 data or persisting signed delivery URLs as identities. Cover status and delivery endpoints SHALL derive tenant scope from the project and verify the caller's current project access even when the image request has no custom tenant header. Display delivery MUST NOT expose the original when processing is pending, failed, or unavailable.

#### Scenario: Project has an uploaded cover
- **WHEN** an uploaded cover is accepted and its display processing completes
- **THEN** the project list and script page resolve to the same authorized compressed object
- **AND** the original dimensions are preserved and the original is not rendered

#### Scenario: Cover is pending or failed
- **WHEN** the cover display object is not READY
- **THEN** the project response and status endpoint expose the appropriate processing state
- **AND** the frontend shows a placeholder without requesting the original

#### Scenario: Unauthorized image request without tenant header
- **WHEN** a caller without current project view access requests cover status or display using only the project identifier
- **THEN** the request is rejected before source reads, conversion, or delivery-grant issuance

### Requirement: Project cover replacement and historical conversion are version safe
The system SHALL convert supported historical uploaded and platform-resource cover sources using the existing idempotent rendition processing flow, retain their stable source identities, and reuse already verified READY display resources. Returning an unchanged stable cover reference in an edit SHALL preserve the stored source; replacing or clearing a cover SHALL be explicit. Concurrent requests and repeated callbacks MUST NOT duplicate conversion or attach a previous version's completion to a new cover. Ordinary project-list reads MUST NOT fetch whole original images or proxy arbitrary external URLs.

#### Scenario: Edit unrelated project fields
- **WHEN** an authorized project edit returns the unchanged cover display reference
- **THEN** the original cover source/version identity remains unchanged and no duplicate processing job is submitted

#### Scenario: Replace a pending cover
- **WHEN** a new cover is selected before the previous cover's job completes
- **THEN** the previous completion only updates its own media version and cannot become the current project's cover

#### Scenario: Two views request a historical uploaded cover
- **WHEN** concurrent authorized requests encounter the same supported historical cover without a display rendition
- **THEN** at most one authoritative original registration and display-processing job exists for that source version
- **AND** both views wait for the compressed display resource rather than rendering the original

#### Scenario: Historical cover is an unsupported external reference
- **WHEN** a historical cover cannot be mapped to a verified platform source or an explicitly configured maintenance import source
- **THEN** its source is retained with a diagnostic processing state and an unavailable placeholder
- **AND** ordinary display requests do not fetch arbitrary external URLs or fall back to the original

### Requirement: Cover processing status refresh is visible and bounded
The frontend SHALL refresh a PENDING cover's authorized status only while its relevant view is visible, at a five-second interval for at most twelve automatic refreshes per active resource. FAILED, MISSING, completed, closed, hidden, or replaced resources SHALL stop automatic refreshes. A user retry SHALL start a new authorized retry attempt without changing the immutable cover source identity. Status and not-ready delivery responses MUST NOT be cached as successful media objects.

#### Scenario: Pending cover becomes ready
- **WHEN** a visible cover status changes from PENDING to READY within the bounded refresh period
- **THEN** the frontend loads its compressed display reference and stops polling

#### Scenario: Leave a pending cover view
- **WHEN** the user navigates away, hides the view, or changes the cover source
- **THEN** pending refreshes are canceled or ignored and cannot overwrite the new view's state

## MODIFIED Requirements

### Requirement: Video covers follow a deterministic source policy
The system SHALL create or reuse one persistent imageSlim display cover per video version when its domain requires or requests a cover. AI-generated video SHALL reuse its bound first-frame image's verified display object; shot and episode composition SHALL prefer the relevant first storyboard frame's verified display object. A source address alone MUST NOT be treated as proof of a compressed cover. Other coverless video SHALL use a Cloud Infinite snapshot at one second with a bounded first-decodable-frame fallback for shorter media. Existing original-resolution display objects SHALL be reused without redundant recompression, and original model input identities SHALL remain unchanged.

#### Scenario: AI video has a bound first frame
- **WHEN** an AI-generated video result is persisted with its authorized first-frame image
- **THEN** the system uses that image's verified imageSlim display object as its cover or creates the required display object if absent
- **AND** it does not decode the video or recompress an already verified display object

#### Scenario: Uploaded video has no cover image
- **WHEN** an uploaded or imported video requires a cover and has no suitable bound image
- **THEN** the system submits a persistent Cloud Infinite snapshot job, applies imageSlim, and records its cover output

#### Scenario: Episode composition disables optional cover generation
- **WHEN** an episode composition request explicitly sets generateCover to false and no domain contract requires a thumbnail
- **THEN** the system does not submit or synthesize a cover

#### Scenario: Shot composition references an original first-frame URL
- **WHEN** a shot or episode cover source references an original first-frame resource
- **THEN** the cover resolver follows the validated source identity to its compressed display object
- **AND** normal cover rendering does not use the original URL while generation inputs keep their existing source identity
