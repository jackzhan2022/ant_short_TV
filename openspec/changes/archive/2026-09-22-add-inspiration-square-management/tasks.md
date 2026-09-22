## 1. Data model and authorization

- [x] 1.1 Add backend migration coverage for inspiration management fields, default existing records to `PUBLISHED`/`IMPORTED`, and register `PLATFORM_INSPIRATION_MANAGE` for the platform administrator role.
- [x] 1.2 Add the database migration for `prompt_text`, `tags_json`, `publish_status`, `source_type`, and `deleted_at`, including indexes for public and management queries.
- [x] 1.3 Extend `InspirationCreationEntity` and response/request types with prompt, tags, publication, source, and deletion metadata while preserving imported-record compatibility.

## 2. Public gallery visibility

- [x] 2.1 Add service and controller tests proving unpublished and deleted records are excluded from public list, detail, original media, and thumbnail endpoints.
- [x] 2.2 Update the public inspiration query guards and response mapping to expose prompt summaries in lists and complete prompts/tags only in details.
- [x] 2.3 Run the focused public inspiration backend tests and confirm existing imported published records remain readable.

## 3. Managed media upload

- [x] 3.1 Add tests for image MIME, decoded dimensions, 1.5MB size enforcement, object paths, and compensation cleanup on failed creation.
- [x] 3.2 Add tests for successful video storage, thumbnail generation, and cleanup when video thumbnail extraction fails.
- [x] 3.3 Implement multipart image/video ingestion by extending the existing inspiration media storage and thumbnail processor integration.
- [x] 3.4 Run the focused upload and storage tests, including unsupported media and over-limit failures.

## 4. Management API

- [x] 4.1 Add authorization tests proving every `/api/platform/inspiration-creations` endpoint requires `PLATFORM_INSPIRATION_MANAGE`.
- [x] 4.2 Add service tests for paginated keyword/status/media filtering, metadata edits, publish/unpublish behavior, atomic reorder validation, and soft deletion.
- [x] 4.3 Implement the platform management controller, command/response types, mapper queries, and transactional service operations.
- [x] 4.4 Add OpenAPI annotations as required by existing controller conventions and regenerate `frontend/src/services/ant-design-pro/` with `npm run openapi` rather than editing generated files directly.
- [x] 4.5 Run the focused management API tests and backend compilation.

## 5. Frontend image processing and services

- [x] 5.1 Add unit tests for proportional image resizing, adaptive encoding to at most 1.5MB, failure preservation, and human-readable before/after sizes.
- [x] 5.2 Implement the browser image compression utility with a 1920px longest-edge bound and deterministic error results.
- [x] 5.3 Add typed management service wrappers for list, multipart create, metadata update, publication change, reorder, and delete operations.
- [x] 5.4 Run the focused image utility and service tests.

## 6. Management drawer interface

- [x] 6.1 Inspect the Ant Design v6 APIs for `Drawer`, `Upload`, `Form`, `Switch`, `Modal`, and list/drag controls with `npx antd info` before writing component code.
- [x] 6.2 Add component tests for administrator-only entry visibility, opening and closing the drawer, search/status/media filters, and loading/error/empty states.
- [x] 6.3 Add form tests for image compression status, video selection, title/tags/prompt validation, successful create/edit, and retained form data after failure.
- [x] 6.4 Add interaction tests for publish/unpublish, deletion confirmation, drag reorder, and simultaneous refresh of the management list and public gallery.
- [x] 6.5 Implement the management drawer and co-located styles/components on the short-drama creation page, keeping the entry hidden for non-admin users.
- [x] 6.6 Extend the public card/detail UI to render prompt summaries, full prompt text, tags, and copy action without eagerly loading original video media.

## 7. Verification and release readiness

- [x] 7.1 Run all focused frontend and backend inspiration tests, then run `npm run lint`, `npx antd lint ./src`, and the relevant backend test suite.
- [x] 7.2 Start the application with mock-compatible data and verify image upload/compression, video thumbnail generation, edit, publish, unpublish, reorder, delete, and ordinary-user visibility in a browser.
- [x] 7.3 Verify desktop and mobile layouts for text overflow, drawer scrolling, fixed control dimensions, and absence of overlapping UI.
- [x] 7.4 Document the migration, new permission, upload limits, storage cleanup behavior, and rollback procedure in the release notes.
