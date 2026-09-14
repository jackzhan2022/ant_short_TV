## ADDED Requirements

### Requirement: New review tasks persist an explicit Markdown result
The system SHALL identify the result format of every newly executed Markdown review and SHALL persist the model's final non-empty Markdown text without parsing its headings, issue fields, evidence structure, score, or conclusion.

#### Scenario: Save free-form Markdown
- **WHEN** a review model returns non-empty Markdown that does not follow a fixed outline
- **THEN** the system stores the returned text as the task report without format rejection
- **AND** marks the task complete after the report write succeeds

#### Scenario: Markdown contains arbitrary structures
- **WHEN** the report contains unknown headings, tables, lists, or field-like labels
- **THEN** the system preserves that content unchanged
- **AND** does not apply a JSON or issue-schema validator

### Requirement: Empty and truncated model results do not complete a review
The system MUST reject null, empty, or whitespace-only final output and MUST keep a provider-reported truncated response from being treated as a complete report.

#### Scenario: Model returns no readable content
- **WHEN** a model call succeeds at the transport layer but returns only whitespace
- **THEN** the review task fails with an actionable empty-output error
- **AND** no completed Markdown report is recorded

#### Scenario: Provider reports output truncation
- **WHEN** the provider reports that a response ended because of an output limit
- **THEN** the system retains any partial text for diagnosis
- **AND** marks the task failed and retryable rather than completed

### Requirement: Markdown results remain bound to immutable review input
Every Markdown report SHALL remain associated with the immutable script version, selected scope, dimensions, mode, and execution attempt that produced it.

#### Scenario: Read a completed report after a later script edit
- **WHEN** the project advances to a newer script version
- **THEN** the prior Markdown report remains readable with its original version and configuration metadata

### Requirement: Historical structured reports remain compatible
The system SHALL continue reading and exporting historical structured review tasks without converting, deleting, or rewriting their result, issue, hit, event, candidate, or semantic-decision data.

#### Scenario: Open a pre-migration structured task
- **WHEN** a user opens a task created before Markdown reporting was enabled
- **THEN** the API identifies it as a structured result
- **AND** returns its existing summary and issues through the historical contract

