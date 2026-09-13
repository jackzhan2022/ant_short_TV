## MODIFIED Requirements

### Requirement: Bound concurrency and freeze stage configuration
The coordinator SHALL enforce a shared configurable provider/model concurrency limit across summary, recognition and storyboard workers, including multiple service instances, and SHALL freeze Agent, Skill, model and episode snapshots per attempt. Each stage SHALL have independently configurable run and request budgets, with request timeout bounded by remaining run time. Queue time SHALL be measured separately and not consume active run budget.

#### Scenario: Agent configuration changes during a stage
- **WHEN** an administrator publishes a new Agent or Skill revision while children remain pending
- **THEN** existing children retain frozen configuration
- **AND** later explicit runs can use the new revision

#### Scenario: Multiple branches compete for capacity
- **WHEN** summary, recognition and storyboard requests exceed the shared limit
- **THEN** excess work remains recoverably queued and active requests do not exceed the configured quota
- **AND** bounded priority prevents indefinite starvation

#### Scenario: Second request exhausts remaining time
- **WHEN** earlier work consumes part of a stage budget
- **THEN** the next request uses at most the remaining budget and timeout diagnostics identify the limiting budget
