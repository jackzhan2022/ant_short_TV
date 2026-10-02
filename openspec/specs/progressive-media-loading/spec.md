# progressive-media-loading Specification

## Purpose
TBD - created by archiving change optimize-media-loading-and-project-covers. Update Purpose after archive.
## Requirements
### Requirement: Business media collections are paginated in the database
The system SHALL read project browsing, image and video task histories, task media results, shot and episode composition histories, episode video versions, style browsing, and asset media candidates through authorized bounded database queries. Newly paginated endpoints SHALL default to current 1 and pageSize 20, cap pageSize at 100, and return items with current, pageSize, and authorized total. Existing paginated endpoints SHALL retain their established parameter names and bounded defaults. The system MUST NOT fetch a complete collection and slice it in memory or provide an unbounded mode to migrated browsing consumers.

#### Scenario: Load the first page of a large media history
- **WHEN** an authorized user opens a collection containing more than 100 records without paging parameters
- **THEN** the database reads only the default page of records and the required count or bounded summary projections
- **AND** the response reports the authorized total and excludes subsequent pages

#### Scenario: Clamp page size and reject unsafe offsets
- **WHEN** a request supplies a pageSize above 100 or pagination values whose offset cannot be computed safely
- **THEN** pageSize is capped at 100 and unsafe offsets are rejected without running an unbounded query

#### Scenario: Preserve deterministic order and empty pages
- **WHEN** records share the primary sort value or a requested page exceeds the final page
- **THEN** records use a unique identifier as a secondary sort key and the out-of-range page returns no items with the actual total

### Requirement: Collection summaries do not expand complete task histories
Task collection records SHALL contain only authorized display metadata, state, progress, counts, and at most two distinct representative results consisting of the selected and latest result. Complete prompts, input snapshots, Base64 image data, and unbounded nested media results MUST NOT be serialized in collection summaries. Result collections and details with media histories SHALL expose bounded previews and explicit result pagination. Database queries SHALL batch representative results for the page rather than perform a result query per task.

#### Scenario: List tasks with many historical results
- **WHEN** the requested task page contains tasks with many media results
- **THEN** the response returns counts and no more than two representative results per task
- **AND** historical results are available through authorized result pagination without a result query per task in the list operation

#### Scenario: Open a task result gallery
- **WHEN** the user opens a task result gallery
- **THEN** only the first bounded result page is requested and further results load through an explicit page or scroll action

### Requirement: Display images load near the viewport
Business image and cover elements SHALL defer their image source until their placeholder enters the viewport or its fixed 200px preload boundary in supported browsers. Hidden tabs, unopened drawers, and unopened previews MUST NOT fetch their image resources. Image dimensions or aspect ratio SHALL remain stable through loading, decode, error, and hover states. Normal rendering MUST NOT fall back to an original image when the compressed display resource is unavailable.

#### Scenario: Image is outside the preload boundary
- **WHEN** an image placeholder remains beyond the viewport's 200px preload boundary
- **THEN** its image element and CSS do not initiate a media request

#### Scenario: Image approaches the viewport
- **WHEN** the placeholder enters the preload boundary
- **THEN** the component assigns the compressed display source and preserves its layout dimensions

#### Scenario: Compressed image is missing
- **WHEN** a compressed image is pending, failed, or cannot be decoded
- **THEN** the element shows its loading or unavailable state without requesting the original as a fallback

### Requirement: Business video bytes load only after explicit playback
Storyboard previews, AI video results, shot and episode composition results, task-center media, and inspiration video details SHALL initially show a compressed cover or a non-media placeholder with an accessible play control. They MUST NOT mount a video source, request a playback grant, fetch video bytes, probe metadata, or decode a video frame before the user explicitly activates playback. After activation the selected resource SHALL be authorized using its stable resource identity and delivered through existing private-media grants and range-capable delivery. Login background autoplay and explicit downloads are outside this requirement.

#### Scenario: Open a video list or detail without playing
- **WHEN** the user opens a business video list, switches its preview tab, or opens video detail
- **THEN** covers can load under the image visibility rule and video byte requests and playback-grant requests remain zero

#### Scenario: Play one video
- **WHEN** the user explicitly activates one video's play control
- **THEN** the system authorizes and mounts only that video's playback source
- **AND** no other list video is fetched or started

#### Scenario: Video has no available cover
- **WHEN** the cover is absent or still processing
- **THEN** the UI provides a placeholder and play control without loading a video to obtain its first frame

### Requirement: Playback and pending requests follow the active view
Each media browsing surface SHALL have at most one active preview player. Changing resource, page, episode, tenant, or project, hiding its tab, closing its drawer, or unmounting SHALL pause and release the active player and invalidate pending playback authorization. The system SHALL reject stale page, cover-status, and playback responses from previous view contexts. Next-page loading SHALL deduplicate concurrent triggers, stop at the last page, and reset on filter changes. Continuous galleries SHALL bound mounted content with a tested windowing or equivalent retention strategy that preserves navigation and scroll position.

#### Scenario: Close preview before authorization completes
- **WHEN** a playback authorization response arrives after the preview is closed or the selected resource changes
- **THEN** it does not mount or start a player for the obsolete resource

#### Scenario: Switch tenant while a page request is pending
- **WHEN** the user changes tenant and an earlier page response subsequently completes
- **THEN** the earlier records do not replace the new tenant's list or remain in its reusable page cache

#### Scenario: Scroll repeatedly through a gallery
- **WHEN** the user loads multiple pages and repeatedly triggers the same next-page boundary
- **THEN** each page is requested once per active load, mounted content remains within its tested bound, and the user retains a stable scroll position

### Requirement: Media loading is verified with observable requests
Verification SHALL cover supported desktop and mobile viewports using multi-page data and historical bound media. Evidence SHALL include actual media network requests and database paging/query counts rather than only DOM attributes. Video requests SHALL be identified by resource identity, content type, range behavior, and request URL without relying exclusively on file extensions. Evidence MUST redact signed query values, credentials, source content, and full prompts.

#### Scenario: Verify initial list load and one playback
- **WHEN** an automated browser opens a large media list, inspects its initial requests, scrolls, and activates one video
- **THEN** initial video byte requests are zero, offscreen images beyond the preload boundary are not fetched, and only the selected video loads after activation
- **AND** the backend reads bounded collection pages without expanding all task results
