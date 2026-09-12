## ADDED Requirements

### Requirement: All integrated production task types expose meaningful details
The system SHALL provide task-specific submitted content, processing settings, persisted outcomes and business progress for IMAGE, VIDEO, SCRIPT_ANALYSIS, SCRIPT_OPERATION, REVIEW, VIDEO_DECOMPOSITION, VIDEO_EPISODE, STORYBOARD_BATCH and STORYBOARD_ITEM. SCRIPT_OPERATION coverage MUST include SCRIPT_GENERATE, SCRIPT_REWRITE, ELEMENT_EXTRACT, SCOPED_ASSET_REEXTRACTION, STORYBOARD_BREAKDOWN and PROMPT_GENERATE. All content in the scenarios below SHALL be shown only where existing records establish its provenance; unavailable fields SHALL use explicit availability states for both old and new tasks. Missing records MUST NOT require new production writes. Completion MUST NOT be claimed by enriching only media tasks or providing only generic status, raw JSON or business links for other supported types.

#### Scenario: Inspect an image generation
- **WHEN** an authorized reader opens an image task with saved inputs and results
- **THEN** the detail shows its saved prompt, negative prompt when applicable, reference images, generation settings and task-owned generated images
- **AND** image dimensions and current selection indicators are labelled according to their actual meaning

#### Scenario: Inspect a video generation
- **WHEN** an authorized reader opens a video task
- **THEN** the detail distinguishes its saved first frame, last frame and other references and displays its prompt, available settings and generated video when present

#### Scenario: Inspect script generation or rewriting
- **WHEN** an authorized reader opens a SCRIPT_GENERATE or SCRIPT_REWRITE operation
- **THEN** the detail shows saved user requirements, scope, available fixed input text and the output version produced by that operation
- **AND** rewriting input and output can be read separately without replacing the source with the current script

#### Scenario: Inspect analysis or asset extraction
- **WHEN** an authorized reader opens SCRIPT_ANALYSIS, ELEMENT_EXTRACT or SCOPED_ASSET_REEXTRACTION
- **THEN** the detail shows the fixed source version, saved processing scope and applicable settings
- **AND** it exposes that task's persisted analysis stages or categorized asset outcomes rather than all current project assets

#### Scenario: Inspect storyboard and prompt operations
- **WHEN** an authorized reader opens STORYBOARD_BREAKDOWN, STORYBOARD_ITEM or PROMPT_GENERATE
- **THEN** the detail presents the relevant fixed episode or target input, saved requirements and the task-linked storyboard entries or generated prompts
- **AND** persisted warnings are readable without exposing internal model request logs

#### Scenario: Inspect a review
- **WHEN** an authorized reader opens a review task
- **THEN** the detail exposes the authorized source draft version, mode, scope and dimensions plus that round's report, findings and suggestions
- **AND** a newer review round does not replace the selected task's result

#### Scenario: Inspect a decomposition episode
- **WHEN** an authorized reader opens a decomposition episode with no AI execution identifier
- **THEN** the detail uses its domain records to show available submitted video metadata, processing information and its persisted screenplay result

### Requirement: Detail sections distinguish availability and provenance
Detail content SHALL expose user-readable sections for overview, submitted content, settings, results and business stages where applicable. The system SHALL distinguish AVAILABLE, PENDING, NOT_RECORDED, DELETED, RESTRICTED and UNSUPPORTED content. It MUST omit inapplicable fields and MUST NOT invent prompts, progress, stages, start times or durations. Task creator, project, creation and completion times SHALL respect existing visibility rules; processing duration and submission-to-completion elapsed time MUST be distinguished when both are available.

#### Scenario: Historical input was not recorded
- **WHEN** a historical task has no reliable saved prompt or source reference
- **THEN** the applicable section explicitly reports that this historical content was not recorded
- **AND** the system does not show today's project input as that task's original submission

#### Scenario: Failed task has a useful partial result
- **WHEN** a failed or partially successful task has authorized persisted outcomes
- **THEN** the detail retains those outcomes and saved inputs alongside a sanitized failure explanation and existing permitted actions

#### Scenario: Unknown operation type
- **WHEN** a historical operation has an unrecognized subtype
- **THEN** the system exposes only verifiable authorized content and an explicit unsupported explanation without falsely labelling it as a supported operation

### Requirement: Details preserve the selected task's input and result identity
The system MUST resolve content through existing saved task fields, source versions, task-bound results or already persisted version/snapshot records. User-entered instructions and an augmented generation prompt SHALL be distinguished when their provenance is known. Internal system prompts and hidden orchestration instructions MUST NOT be included. Later edits, task retries, regeneration and shared execution MUST NOT silently substitute another task's input or output.

#### Scenario: Project content changes after submission
- **WHEN** the project script, asset prompt or reference selection changes after a task was submitted
- **THEN** the task detail continues to show its recorded submission or an explicit missing-record state

#### Scenario: Only an augmented prompt is available
- **WHEN** the persisted prompt includes domain augmentation and no original user prompt was saved
- **THEN** the detail labels it as the saved generation prompt and does not claim it is the verbatim user submission

#### Scenario: Regeneration produces a later result
- **WHEN** a task is regenerated or a later operation replaces the current business asset
- **THEN** the detail shows the original task-bound outcome if it remains verifiably recorded, otherwise an explicit unavailable state; it never substitutes the later outcome
- **AND** any current adoption marker is explicitly labelled as current state

#### Scenario: A batch reuses another user's execution
- **WHEN** a storyboard item reuses an execution originally submitted by a different user
- **THEN** the detail distinguishes batch submitter from execution origin and exposes only content allowed by the original domain authorization
- **AND** the reused execution grants no new control or private-input access

### Requirement: Detail enhancement does not extend production write workflows
The enhancement SHALL read existing records only. It MUST NOT introduce content snapshot tables, schema migrations, submission or result-save hooks, background capture, backfills or media ingestion. Business creation, dispatch, execution, callbacks, persistence, controls and billing MUST remain independent of detail reads and unchanged by this enhancement. Viewing details MUST NOT create tasks, write back content, initiate AI work, alter billing or fetch arbitrary external media to reconstruct history.

#### Scenario: Detail service is unavailable during production
- **WHEN** a user submits or runs a business task while detail reads are disabled, failing or timing out
- **THEN** the original submission, execution and result-save paths continue without invoking or awaiting detail capture or queries

#### Scenario: A new task lacks its original instructions
- **WHEN** a newly created task has no reliable persisted original instructions
- **THEN** its detail reports NOT_RECORDED without capturing or writing back content

#### Scenario: A mutable outcome is superseded
- **WHEN** a source without a pre-existing immutable result stores an outcome and later overwrites the business resource
- **THEN** the detail reports the original outcome as unavailable or unverifiable without substituting the current resource or creating a snapshot

### Requirement: Detail reads protect production responsiveness
Content reads SHALL be on demand, scoped and bounded in database work as well as response size. They MUST NOT use locking reads, scan all task history, load all sibling contents or hold production write transactions while reading media. Detail query timeouts and concurrency limits SHALL bound shared resource consumption. Performance acceptance SHALL fix the workload and acceptable business latency/throughput regression budget before comparing a baseline with concurrent detail browsing; exceeding that budget MUST fail acceptance.

#### Scenario: Detail drawer remains closed
- **WHEN** a user browses the task list without opening a detail
- **THEN** no detail-content query or media-body request is issued

#### Scenario: Concurrent detail requests exceed their budget
- **WHEN** detail reads reach their configured concurrency or timeout budget
- **THEN** excess or timed-out detail requests receive a bounded retryable failure without introducing a dependency into business submission or execution

#### Scenario: Validate production under concurrent browsing
- **WHEN** the same production workload is measured before and during concurrent detail browsing in the target MySQL environment
- **THEN** evidence records submission latency, production-stage latency, throughput and detail query cost against the predeclared budget rather than claiming that read-only work has zero cost

### Requirement: Text and media details remain bounded and usable
The system SHALL provide expandable and copyable authorized text, zoomable reference/result images, on-demand video playback and authorized download actions where the domain supports them. Initial text previews MUST be limited to 4,000 characters per preview, text chunks to 32,000 characters, collection pages to a default of 20 and maximum of 100, and the initial detail-content JSON to 256 KiB. Truncated content SHALL expose a continuation mechanism. Media bytes and base64 MUST NOT be embedded in detail JSON. Raw HTML MUST NOT execute in text/report rendering.

#### Scenario: Open a large script result
- **WHEN** a result exceeds the preview limit
- **THEN** the initial response identifies truncation and allows the reader to request subsequent bounded content
- **AND** a copy-full-text action retrieves the complete authorized content instead of silently copying the truncated preview

#### Scenario: One generated image is unavailable
- **WHEN** an image resource fails to load or has been deleted
- **THEN** that item's unavailable state is shown while other authorized images and text remain usable

#### Scenario: A video preview is unopened
- **WHEN** the detail lists a generated video but the user has not started its preview
- **THEN** the browser does not eagerly download the video body

### Requirement: Batch details support focused child inspection
Batch details SHALL display recorded batch inputs, aggregate progress and paged child summaries. Selecting a child SHALL load only that child's authorized content and preserve the parent task, parent child-page position and current list query. Returning to the parent SHALL restore that context. Child content MUST NOT be bulk-loaded for all siblings.

#### Scenario: Inspect the second page of a batch
- **WHEN** a user opens a child from the second page and returns to the parent
- **THEN** the same batch child page and outer task filters are restored without loading other children's full inputs or results

#### Scenario: Batch is empty
- **WHEN** a recorded batch has no children
- **THEN** the detail shows its actual batch information and a clear empty-child state without fabricating child outputs
