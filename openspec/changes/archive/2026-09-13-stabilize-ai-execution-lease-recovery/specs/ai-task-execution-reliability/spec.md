## ADDED Requirements

### Requirement: Active execution claims are renewed and fenced
The system SHALL periodically renew the claim for a healthy running AI execution before its configured expiry, and MUST prevent an attempt that loses claim ownership from publishing execution-terminal or domain-terminal state.

#### Scenario: Long-running handler remains healthy beyond the original claim expiry
- **WHEN** a claimed execution handler remains active longer than the initial claim timeout
- **THEN** the worker renews the same token-qualified claim before expiry
- **AND** the dispatcher does not recover or duplicate the healthy execution

#### Scenario: Heartbeat discovers that ownership was lost
- **WHEN** a heartbeat cannot renew the execution because its claim token or running state no longer matches
- **THEN** the prior attempt is treated as having lost execution ownership
- **AND** it does not mark the execution, domain task, or domain stage succeeded or failed

#### Scenario: Replacement attempt starts before the old handler returns
- **WHEN** an expired attempt returns after a replacement attempt has started
- **THEN** attempt fencing rejects all stale terminal state writes
- **AND** the replacement attempt remains the authoritative owner

### Requirement: Execution heartbeat configuration is bounded
The system SHALL require a positive heartbeat interval shorter than the configured execution claim timeout.

#### Scenario: Heartbeat timing is valid
- **WHEN** the application starts with a heartbeat interval greater than zero and lower than the claim timeout
- **THEN** workers use that interval for active claim renewal

#### Scenario: Heartbeat timing cannot renew before expiry
- **WHEN** the configured heartbeat interval is zero, negative, or not lower than the claim timeout
- **THEN** application configuration fails before workers dispatch AI executions
