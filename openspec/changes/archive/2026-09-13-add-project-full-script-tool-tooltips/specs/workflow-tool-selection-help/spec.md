## ADDED Requirements

### Requirement: Agent editor explains each workflow tool
The Agent editor SHALL expose the catalog description of every workflow tool through hover help wherever a user selects, reviews, or inserts a tool. The visible tool name and code SHALL remain available without requiring hover.

#### Scenario: User hovers a tool in the allowed-tools selector
- **WHEN** a user hovers a tool option in the allowed-tools multi-select control
- **THEN** the interface displays that tool's catalog description

#### Scenario: User reviews a selected tool
- **WHEN** a user hovers a selected workflow-tool value
- **THEN** the interface displays that tool's catalog description

#### Scenario: User inserts a tool prompt helper
- **WHEN** a user hovers an "insert tool call instruction" button
- **THEN** the interface displays that tool's catalog description
