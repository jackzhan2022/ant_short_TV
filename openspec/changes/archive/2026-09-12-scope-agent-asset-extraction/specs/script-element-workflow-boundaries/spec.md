## ADDED Requirements

### Requirement: Asset settings migrates away from legacy element extraction
The asset-settings batch-generation control SHALL use scoped formal recognition and SHALL NOT invoke the legacy raw JSON element-extraction operation. The legacy API remains available only for remaining compatible consumers during migration.

#### Scenario: Asset-settings user requests batch generation
- **WHEN** a user clicks batch generation on an asset-settings scope
- **THEN** the client performs scoped re-extraction preflight and submits the formal recognition operation when appropriate
- **AND** it does not create a legacy candidate-normalization run
