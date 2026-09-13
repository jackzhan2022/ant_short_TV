## MODIFIED Requirements

### Requirement: Library state is derived from result-format-aware review data
The library SHALL derive display-only work states from the latest review task and its result format. A completed Markdown report SHALL be treated as completed review work without requiring structured issues, while historical structured tasks SHALL continue using issues and manual-resolution markers.

#### Scenario: Identify a completed Markdown review
- **WHEN** the latest completed task has result format `MARKDOWN` and a saved report
- **THEN** the library displays the project as completed
- **AND** does not classify the project as unreviewed because its issue list is empty

#### Scenario: Identify a historical project requiring issue handling
- **WHEN** the latest structured task has one or more issues that are not manually resolved
- **THEN** the library displays the project as requiring issue handling
- **AND** shows the outstanding issue count when it is available

#### Scenario: Identify a historical project ready for re-review
- **WHEN** the latest structured task has issues and all of them are manually resolved
- **THEN** the library displays the project as ready for re-review
- **AND** does not persist this display state as a new server-side status
