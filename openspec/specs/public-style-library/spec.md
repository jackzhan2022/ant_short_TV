# public-style-library Specification

## Purpose
TBD - created by archiving change add-public-style-library. Update Purpose after archive.
## Requirements
### Requirement: Public style records are seeded

The system SHALL persist the provided public style library records in a backend table with stable source identifiers, display metadata, classification, original image URL, object storage path, display image URL, image dimensions, public visibility, and deterministic ordering.

#### Scenario: Seed imports provided public styles

- **WHEN** the database migration for the public style library runs
- **THEN** the system stores all provided public style records with their name, description, source ID, original image URL, object storage path, display image URL, image dimensions, public flag, and sort order

#### Scenario: Seed can be rerun safely in development

- **WHEN** the seed/import logic encounters a style record with an existing source ID
- **THEN** the system does not create a duplicate public style record for that source ID

### Requirement: Style categories are derived for browsing

The system SHALL assign each public style a browsing category derived from the style name prefix before the first `-` or `－` separator, and SHALL assign `未分组` when no separator is present.

#### Scenario: Name with prefix is categorized

- **WHEN** a style named `3D风格-高清真实渲染` is imported
- **THEN** the system stores its category as `3D风格`

#### Scenario: Name without prefix is categorized as ungrouped

- **WHEN** a style name has no `-` or `－` separator
- **THEN** the system stores its category as `未分组`

### Requirement: Reference images are stored in the platform object bucket

The system SHALL download each provided public style reference image during import and store it in the platform object bucket under a public style library path that is not scoped to a tenant or project.

#### Scenario: Reference image is transferred to object storage

- **WHEN** a public style record is imported with a valid source image URL
- **THEN** the system stores the image in the platform object bucket and saves both the source image URL and object storage path

#### Scenario: Object storage path is platform-scoped

- **WHEN** the system stores a public style reference image
- **THEN** the storage path uses a platform public style prefix such as `style-library/public/{externalId}/cover.{ext}` and does not use a project material path

#### Scenario: Display image URL uses stored object

- **WHEN** the public style API returns an imported style record
- **THEN** the returned image URL points to the object-bucket-backed display image rather than the original source image URL

### Requirement: Public style API supports read-only browsing

The system SHALL expose a read-only API for authenticated users to query public styles by optional keyword and category filters.

#### Scenario: Query all public styles

- **WHEN** an authenticated user requests the public style library without filters
- **THEN** the system returns all public style records ordered deterministically for grid browsing

#### Scenario: Query by category

- **WHEN** an authenticated user requests public styles with a category filter
- **THEN** the system returns only public style records in that category

#### Scenario: Query by keyword

- **WHEN** an authenticated user requests public styles with a keyword
- **THEN** the system returns public style records whose name or description contains the keyword

#### Scenario: Unauthenticated query is rejected

- **WHEN** a request without a valid authenticated user queries the public style library API
- **THEN** the system rejects the request using the existing authentication behavior

### Requirement: Style library menu is available

The frontend SHALL provide a first-level `风格库` menu entry that routes authenticated users to the public style library page.

#### Scenario: Authenticated user sees style library menu

- **WHEN** an authenticated user opens the application layout
- **THEN** the system shows a first-level `风格库` menu entry

#### Scenario: Selecting style library opens the grid page

- **WHEN** the user selects the `风格库` menu entry
- **THEN** the system navigates to `/style-library` and loads the public style library page

### Requirement: Public styles are displayed as a responsive grid

The frontend SHALL display public styles as a responsive grid of cards with reference image, style name, category, and description.

#### Scenario: Grid renders style cards

- **WHEN** the public style API returns style records
- **THEN** the page renders each style as a card containing the reference image, name, category, and description

#### Scenario: Image preview is available

- **WHEN** the user opens a style reference image preview
- **THEN** the system displays the larger image using the existing image preview behavior

#### Scenario: Empty result is clear

- **WHEN** filters return no public style records
- **THEN** the page shows an empty state instead of a blank grid

### Requirement: Grid browsing supports filters

The frontend SHALL allow users to filter the public style grid by category and keyword without exposing edit or apply actions.

#### Scenario: Category filter updates grid

- **WHEN** the user selects a category filter
- **THEN** the page reloads or filters the grid to show styles from that category

#### Scenario: Keyword search updates grid

- **WHEN** the user enters a keyword in the search control
- **THEN** the page reloads or filters the grid to show styles matching the keyword in name or description

#### Scenario: No production action is shown

- **WHEN** the public style library page is rendered
- **THEN** the page does not show actions to edit, delete, apply, copy to project, or create image generation tasks from a style

