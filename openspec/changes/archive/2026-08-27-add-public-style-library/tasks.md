## 1. Data Model and Seed

- [x] 1.1 Add Flyway migration for `style_library` with source ID uniqueness, category, description, source image URL, object storage path, display image URL, image metadata, public flag, sort order, timestamps, and query indexes.
- [x] 1.2 Add backend entity, mapper, and response DTO for public style records.
- [x] 1.3 Add seed/import data for the provided 139 public styles, deriving category from the name prefix and preserving source IDs, source image URLs, and image dimensions.
- [x] 1.4 Add platform object-bucket transfer logic that downloads each source reference image, stores it under `style-library/public/{externalId}/cover.{ext}`, and records storage/display URLs without using project material paths.
- [x] 1.5 Add backend tests that verify seed count, source ID de-duplication, category derivation, object storage path derivation, and deterministic ordering.

## 2. Backend Query API

- [x] 2.1 Implement a public style library service that queries only public styles and supports optional keyword and category filters.
- [x] 2.2 Add `GET /api/style-library` controller endpoint using existing authentication context and returning browse-ready fields.
- [x] 2.3 Add controller/service tests for all records, category filter, keyword filter, empty results, object-bucket-backed image URLs, and unauthenticated access behavior.

## 3. Frontend Style Library Page

- [x] 3.1 Add frontend service types and request helper for querying `/api/style-library` without editing generated service files.
- [x] 3.2 Add `/style-library` route, `风格库` first-level menu entry, `canViewStyleLibrary` access rule, and menu locale entries.
- [x] 3.3 Implement the style library page with search, category filtering, loading/empty states, and responsive grid cards.
- [x] 3.4 Render each style card with reference image preview, name, category tag, and clamped description, with no edit/apply/project actions.
- [x] 3.5 Add frontend tests for route/menu visibility, API querying, grid rendering, category filtering, keyword search, empty state, and absence of production actions.

## 4. Verification

- [x] 4.1 Run backend schema and targeted style library tests, including object storage transfer/path coverage.
- [x] 4.2 Run frontend targeted tests for the style library page and route visibility.
- [x] 4.3 Run required project checks: backend tests impacted by migration, frontend type/lint/tests, and `npx antd lint ./src`.
- [x] 4.4 Manually verify the page loads all 139 imported styles, filters by major categories, searches names/descriptions, and previews images.
