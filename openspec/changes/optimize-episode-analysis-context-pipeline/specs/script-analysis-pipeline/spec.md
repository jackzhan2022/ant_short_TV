## MODIFIED Requirements

### Requirement: Execute the four analysis stages in order
The system SHALL execute global understanding followed by validated splitting, then independently schedule episode summary and asset recognition branches over the frozen episode set. It SHALL NOT require summary completion or summary output for recognition. Each stage SHALL use its enabled workflow Agent and trusted current source; optional global context SHALL be frozen. Automatic storyboards SHALL depend only on their episode's successful asset coverage and have separate status.

#### Scenario: Advance after committed formal output
- **WHEN** splitting commits valid formal episodes
- **THEN** both branches become eligible independently
- **AND** each current episode with committed asset coverage can trigger its storyboard

#### Scenario: Preserve failed stage
- **WHEN** a branch fails before formal completion
- **THEN** its failure and earlier committed output are preserved
- **AND** other eligible branches continue without being marked successful prematurely

#### Scenario: Complete analysis independently of storyboard
- **WHEN** all four analysis stages pass current formal coverage
- **THEN** analysis becomes completed while storyboard outcomes remain separately visible
