## ADDED Requirements

### Requirement: Validate explicit episode granularity
The split validator SHALL distinguish episode headings from multi-episode group labels and require all high-confidence individual episode boundaries in AI output in addition to exact source coverage. Ambiguous headings SHALL produce an actionable validation outcome; titleless scripts SHALL retain AI boundary inference.

#### Scenario: Fifty-nine episodes in eight narrative groups
- **WHEN** the source contains individual episodes 1 through 59 within narrative group labels
- **THEN** saving eight merged group episodes is rejected
- **AND** only boundaries preserving the validated individual episodes can succeed

#### Scenario: Directory or quoted heading
- **WHEN** heading-like text is a table of contents, quotation, duplicate or ambiguous number
- **THEN** the validator distinguishes it from trusted body boundaries or reports ambiguity instead of silently splitting on every match

#### Scenario: No explicit headings
- **WHEN** the script has no trustworthy episode labels
- **THEN** existing AI inference and bounded chunk fallback remain available with exact coverage validation
