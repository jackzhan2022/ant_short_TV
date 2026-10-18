## ADDED Requirements

### Requirement: One storyboard production Skill
The built-in storyboard Agent SHALL use `short-drama-storyboard-production` as its single consolidated built-in Skill. The document SHALL cover factual fidelity, directing, spatial continuity, asset references, sound and existing output fields. Other Agents' shared Skills and user-added associations MUST be preserved.

#### Scenario: Existing Agent is upgraded
- **WHEN** an Agent uses the four original built-in storyboard Skills
- **THEN** those associations SHALL become one unified Skill without changing model, tool or generation settings.

### Requirement: Directing rules adapted to platform contracts
The unified Skill SHALL include dialogue/reaction, action/result, process, emotional decision, reveal, transition and ability demonstrations. It SHALL require visible state changes, restrained camera movement, faithful voices and continuous props/positions while retaining Schema 3 and current duration limits.

#### Scenario: A storyboard is saved
- **WHEN** the Agent follows the unified Skill
- **THEN** the existing save tool SHALL validate source coverage, durations and material identities, derive original sounds and retain rich-text material nodes; model-supplied dialogue or IDs SHALL NOT gain authority.

### Requirement: Lossless tenant supplement consolidation
Tenant planning and video-expression documents SHALL be archived completely before being consolidated into one tenant Skill, in their original priority order. Actors, dates and source revisions SHALL remain recoverable. Re-running the migration MUST NOT duplicate sections or change already migrated data.

#### Scenario: Both old tenant documents exist
- **WHEN** the migration runs
- **THEN** both original documents SHALL remain in the archive, their bodies SHALL appear in the unified supplement in planning/video order, and only one active tenant document SHALL remain.

### Requirement: One editable tenant supplement with existing precedence
The tenant UI/API SHALL expose only the unified Skill type for new/edit operations, with ordinary-member read-only access and owner/admin mutations. System and tenant documents SHALL remain separate runtime layers with priorities 100 and 200.

#### Scenario: A team edits its unified supplement
- **WHEN** an authorized team manager saves the unified Skill
- **THEN** only that team's supplement SHALL change and later storyboard runs SHALL include both system and tenant layers; legacy type mutations SHALL be rejected.
