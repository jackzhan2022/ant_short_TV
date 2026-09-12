## ADDED Requirements

### Requirement: Apply confirmed prompt policy during scoped recognition
The system SHALL apply the user-confirmed prompt policy after formal asset recognition for the submitted scope. `FILL_EMPTY` MUST preserve non-empty prompts; `REGENERATE_ALL` MUST be constrained to the submitted scope.

#### Scenario: Regenerate only props
- **WHEN** a user confirms `PROP` re-extraction with `REGENERATE_ALL`
- **THEN** the system regenerates canonical prop and prop-state prompts in that scope
- **AND** character and scene prompts remain unchanged

#### Scenario: Concurrent manual prompt edit
- **WHEN** a user manually updates a prompt after preflight but before a `FILL_EMPTY` task writes it
- **THEN** the task does not replace that now non-empty prompt

## MODIFIED Requirements

### Requirement: Store prompts without overwriting established content
The system SHALL store generated prompts in the existing canonical asset and visual-variant prompt columns within the same transaction that saves episode assets. During standard recognition and `FILL_EMPTY` scoped recognition, it MUST write prompts for new records and fill empty prompts on matched records, and MUST NOT replace an existing non-empty prompt. During a user-confirmed `REGENERATE_ALL` scoped recognition, it SHALL replace prompts only for records in the submitted scope and SHALL not affect other asset types.

#### Scenario: New asset payload is valid
- **WHEN** a new asset and its required prompt pass schema, evidence, identity, and ownership validation
- **THEN** the asset and prompt are committed atomically

#### Scenario: Matched asset has an empty prompt
- **WHEN** the Agent matches an existing asset reported with `hasPrompt=false` and supplies a valid prompt
- **THEN** the system fills that empty prompt in the same transaction

#### Scenario: Matched asset already has a prompt
- **WHEN** the Agent matches an existing asset reported with `hasPrompt=true` during standard recognition or `FILL_EMPTY` scoped recognition
- **THEN** the existing prompt remains unchanged
- **AND** the Agent is not required to reproduce the prompt

#### Scenario: User confirms scoped prompt regeneration
- **WHEN** a user explicitly confirms `REGENERATE_ALL` for `SCENE`
- **THEN** matching scene and scene-variant prompts in that operation scope are replaced with generated values
- **AND** character and prop prompts remain unchanged
