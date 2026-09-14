# asset-extraction-coordination Specification

## Purpose
TBD - created by archiving change stabilize-asset-extraction-concurrency. Update Purpose after archive.
## Requirements
### Requirement: Atomically admit equivalent extraction requests
The system SHALL coordinate extraction by tenant, project and script before creating an operation or reserving points. Equivalent active requests SHALL share one task only when submitting user, source version and episode fingerprints, scope, prompt policy and effective model configuration match. Non-equivalent concurrent requests SHALL return a conflict with an authorized task reference without creating another reservation.

#### Scenario: Duplicate clicks have different client keys
- **WHEN** two equivalent requests arrive concurrently with different client idempotency keys
- **THEN** both receive the same execution ID and only one operation and point reservation exist

#### Scenario: Scope or policy differs
- **WHEN** an ALL request is active and a CHARACTER request arrives, or a different prompt policy or user submits
- **THEN** the new request receives a conflict without changing the active task or its billing ownership

### Requirement: Persist extraction ownership across entry points
The system SHALL allow only one extraction operation owner per tenant/project/script across script analysis and scoped re-extraction. Ownership SHALL be fenced by execution ID, version and current attempt; different scripts SHALL remain independently executable. Database transactions SHALL NOT span model network calls.

#### Scenario: Script analysis reaches recognition during re-extraction
- **WHEN** scoped re-extraction owns the script and script analysis is scheduled
- **THEN** analysis waits through persisted scheduling before any model invocation and retains acquired ownership through its asset stage and execution settlement

#### Scenario: Worker resumes after lease takeover
- **WHEN** an old worker resumes after a new attempt takes ownership
- **THEN** its save, finalization and ownership release are rejected

#### Scenario: Terminal task releases ownership
- **WHEN** an owner succeeds, fails or is canceled
- **THEN** subsequent admission or acquisition conditionally reclaims the terminal owner's record under lock, without accepting late writes from the former owner

### Requirement: Preserve idempotent formal persistence
Formal writes SHALL atomically validate execution authority, source fingerprint and commit evidence, reuse exact canonical or explicit alias identity, and keep variants and episode bindings unique within their owners. The system SHALL preserve existing identity locks and prompt policy behavior and SHALL reject ambiguous identity rather than merge guesses.

#### Scenario: Two child runs introduce the same asset
- **WHEN** concurrent children of an authorized operation save the same normalized asset
- **THEN** one canonical identity is persisted and their legitimate episode bindings are retained without duplicate variants or bindings

#### Scenario: Commit succeeds before worker crashes
- **WHEN** persistence commits but the worker crashes before recording unit success
- **THEN** recovery reuses the persisted commit evidence and does not regenerate the committed unit

#### Scenario: Historical duplicates prevent uniqueness migration
- **WHEN** migration preflight finds conflicting active identities
- **THEN** it reports the conflicts without deleting or silently merging records

### Requirement: Finalize only complete owned extraction
Finalization SHALL revalidate execution ownership and frozen source version in the write transaction and SHALL require committed results for all frozen units. It SHALL be idempotent and restricted to unreferenced AI assets and variants in the selected scope, preserving manual, out-of-scope and newer-version data.

#### Scenario: A unit failed or source changed
- **WHEN** any frozen unit lacks successful commit evidence or the source version changed
- **THEN** the operation does not retire old assets

#### Scenario: Retry after finalization
- **WHEN** the same successful operation retries finalization
- **THEN** no additional assets or bindings are retired and ownership remains consistent

### Requirement: Distinguish trusted analysis and operation identities
The system SHALL authorize asset recognition against its persisted business type and matching task or operation. It SHALL NOT infer operation identity solely from Agent code or use operation IDs as analysis task IDs for contextual reads.

#### Scenario: Both formal entry points run
- **WHEN** script-page recognition uses SCRIPT_ANALYSIS_TASK and asset-page re-extraction uses SCRIPT_AI_OPERATION
- **THEN** each is authorized against its own matching persisted business record and retains its fixed model identity

#### Scenario: Identity or tenant mismatches
- **WHEN** a request supplies an unrelated business ID, user, tenant, project, script or expired attempt
- **THEN** authorization fails before model execution or asset writes
