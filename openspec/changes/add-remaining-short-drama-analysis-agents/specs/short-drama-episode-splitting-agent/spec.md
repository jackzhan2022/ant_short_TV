## ADDED Requirements

### Requirement: Provide an independently runnable episode-splitting Agent
The system SHALL provide an enabled workflow Agent identified by `short-drama-episode-splitting` that can independently split the current script selected through trusted execution scope.

#### Scenario: Run splitting independently after a script edit
- **WHEN** an authorized user explicitly runs the Agent after saving new current script content
- **THEN** the Agent reads that current content rather than a historical analysis-result payload
- **AND** does not require a successful global-understanding result

### Requirement: Load splitting Skills and only mode-specific required tools
The Agent SHALL load `short-drama-analysis-foundation` followed by `short-drama-episode-splitting-framework`, SHALL expose only `read_current_script`, `read_script_structure`, `analyze_script_chunks`, and `save_episode_splitting`, and SHALL preserve the Skill and tool snapshots in its Run. The Run contract SHALL permit exactly one of two ordered paths: `read_current_script -> save_episode_splitting` or `read_script_structure -> analyze_script_chunks -> save_episode_splitting`.

#### Scenario: Start a configured split Run
- **WHEN** the Agent Run starts
- **THEN** both Skills are loaded in the required order
- **AND** the model can call only the trusted full-read, fallback-analysis, and split-save tools

### Requirement: Prefer non-thinking full-script boundary analysis
The Agent SHALL first attempt complete-script boundary analysis when the estimated request fits the configured safe context budget, SHALL request non-thinking mode from supported DeepSeek-compatible models, and SHALL prohibit explanatory output or source repetition before the save tool call.

#### Scenario: Full script fits the safe request budget
- **WHEN** the composed prompt, tool schemas, complete script, and reserved tool output fit the configured threshold
- **THEN** the Agent executes `read_current_script -> save_episode_splitting`
- **AND** submits only ordered titles and exact source markers

### Requirement: Fall back only for capacity or incomplete-call failures
The same user-visible split Run SHALL switch at most once to an isolated chunk fallback phase when preflight exceeds the safe context threshold, the provider reports a context-length failure, the response is truncated, the response is empty, or the required save tool is not called. Failed generated text SHALL NOT enter the fallback context.

#### Scenario: Full-script response reaches the output limit
- **WHEN** the model returns `finish_reason=length` without a successful save tool call
- **THEN** the Run records the full-path call and fallback reason
- **AND** continues through `read_script_structure -> analyze_script_chunks -> save_episode_splitting`

#### Scenario: Submitted boundaries fail business validation
- **WHEN** the model calls the save tool but markers are repeated, stale, overlapping, reversed, or incomplete
- **THEN** the Run does not use chunk fallback to hide the validation error
- **AND** follows the typed correction or failure policy

### Requirement: Analyze fallback chunks as internal units
The fallback service SHALL create structure-aware overlapping chunks without treating chunk boundaries as episode boundaries, SHALL analyze each chunk through audited AI calls with bounded concurrency, and SHALL return verified deduplicated candidates plus trusted anchors to a clean aggregation call.

#### Scenario: Script has no explicit episode labels
- **WHEN** fallback analyzes a long script without `第 N 集` headings
- **THEN** chunk construction uses scene, time/location, paragraph, line, and safe-length signals
- **AND** AI candidates still determine the formal episode boundaries

#### Scenario: One chunk fails
- **WHEN** any chunk AI call fails or returns unverifiable candidates
- **THEN** no formal episode save occurs
- **AND** a retry reuses unchanged successful chunks and schedules only failed or missing chunks

### Requirement: Persist restorable fallback state
The system SHALL persist one source-hash split snapshot and its chunk-unit statuses, bounded candidate results, call references, fallback reason, and progress without duplicating the full script text.

#### Scenario: Page reloads during fallback
- **WHEN** a user returns while chunk analysis is active
- **THEN** the API restores full-versus-fallback mode, total chunks, completed chunks, failed chunks, and current action
- **AND** does not restart successful hash-matching chunks

### Requirement: Always use AI to identify episode boundaries
The Agent MUST invoke the configured AI model for every split Run, including scripts that already contain explicit episode headings, and SHALL return ordered titles and source boundary markers rather than rewritten episode bodies.

#### Scenario: Current script has explicit headings
- **WHEN** the current script contains multiple recognizable episode headings
- **THEN** the Agent still invokes AI to determine and submit all episode boundaries
- **AND** no local parser bypass marks the stage successful

### Requirement: Validate and extract exact source coverage
`save_episode_splitting` SHALL resolve all submitted markers against the server-recorded script snapshot, extract exact source substrings, and require ordered, non-overlapping, complete non-whitespace coverage before persistence.

#### Scenario: Valid boundaries cover the script
- **WHEN** submitted markers resolve in order and cover the complete current script
- **THEN** the tool extracts each episode body from the exact script snapshot
- **AND** persists no model-rewritten body text

#### Scenario: Boundaries omit or overlap content
- **WHEN** markers are missing, duplicated, reversed, overlapping, or leave non-whitespace content uncovered
- **THEN** the tool rejects the payload with an actionable validation error
- **AND** does not silently persist a one-episode fallback

### Requirement: Reconcile formal episodes with stable identities
The save tool SHALL reconcile the validated episode set against current active `script_episode` rows, retain IDs for strongly matched episodes, create IDs for new episodes, and retire disappeared episodes in one transaction.

#### Scenario: A matched episode changes title or content
- **WHEN** reconciliation strongly matches a generated episode to an active formal episode
- **THEN** the system retains the existing `episode_id`
- **AND** overwrites its number, title, exact content, fingerprint, and current generation provenance

#### Scenario: An episode disappears
- **WHEN** a previously active episode has no match in the complete validated split
- **THEN** the system retires that episode without deleting it
- **AND** retains its downstream variant bindings as inactive or retired without a review-required status

### Requirement: Reject stale and incomplete split Runs
The split Agent Run SHALL succeed only after a successful current-script read followed by a committed `save_episode_splitting` for the unchanged source hash.

#### Scenario: Script changes before save
- **WHEN** current script content changes after `read_current_script` and before the save tool executes
- **THEN** the tool returns a stale-source error
- **AND** writes no episode changes

#### Scenario: Model stops without saving
- **WHEN** the model returns final text without a successful split-save tool call
- **THEN** the Run fails with the required-tool error
- **AND** the analysis stage does not succeed
