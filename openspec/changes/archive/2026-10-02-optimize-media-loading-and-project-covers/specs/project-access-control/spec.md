## MODIFIED Requirements

### Requirement: Project discovery follows effective access
The system SHALL make every non-deleted project in the selected tenant discoverable through paginated queries for tenant owners, tenant administrators, and users with tenant-wide project view permission. Other active tenant members SHALL discover only projects where they have an active project membership and effective project view access. Visibility filtering SHALL occur in both the database page query and its total-count query before pagination. Page records SHALL retain existing project capabilities and owner/member summaries without reading every project into application memory.

#### Scenario: Tenant administrator lists projects
- **WHEN** a tenant administrator requests a project page
- **THEN** the response contains only that bounded page of non-deleted projects in the selected tenant
- **AND** the reported total covers all discoverable projects and subsequent pages make them reachable

#### Scenario: Ordinary member lists projects
- **WHEN** an ordinary member assigned only to project A requests the project list
- **THEN** the response includes project A when it belongs on the requested page and excludes other tenant projects
- **AND** the total counts only projects the member can view

#### Scenario: Removed project member lists projects
- **WHEN** a user's project membership is removed
- **THEN** that project is absent from the user's next accessible-project page and its total unless the user has tenant-wide access

#### Scenario: Hidden projects precede visible projects in sort order
- **WHEN** an ordinary member's visible projects occur after inaccessible projects in the global project order
- **THEN** the authorized page is populated from the filtered visible set rather than filtering a page of all projects afterward
- **AND** no hidden project count or metadata is disclosed
