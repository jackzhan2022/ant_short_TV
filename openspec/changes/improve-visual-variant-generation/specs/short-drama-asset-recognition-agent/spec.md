## ADDED Requirements

### Requirement: Preserve visual-variant prompt derivation inputs
The per-episode asset recognition Agent SHALL save each recognized character look and prop state as a visual variant with its recognized name and visual description available for later prompt derivation.

#### Scenario: Agent recognizes a character costume
- **WHEN** the Agent identifies a production-visible costume, hair, makeup, or accessory change
- **THEN** the saved character visual variant retains the recognized look name and description
- **AND** the description is available to the visual-variant prompt derivation flow
