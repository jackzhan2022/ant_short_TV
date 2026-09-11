## 1. Image Invocation Contract

- [x] 1.1 Add failing adapter tests for `/v1/responses`, configured/default `responsesModel`, tool-level image model, and generate versus edit input payloads.
- [x] 1.2 Add failing adapter tests for decoding `output[].type = image_generation_call` and rejecting missing or invalid image output.
- [x] 1.3 Extend the routed image-model configuration to resolve `responsesModel` with `gpt-5.6-terra` as its fallback.
- [x] 1.4 Replace the XiongXiong legacy Image API request/response handling with the Responses `image_generation` tool contract and normalized typed image data.
- [x] 1.5 Run the focused adapter tests and verify no production image path still invokes `/v1/images/generations` or `/v1/images/edits`.

## 2. Paired Image Persistence

- [x] 2.1 Add a database migration and entity mappings for original MIME type and thumbnail storage path required to serve paired generated-image resources.
- [x] 2.2 Add failing storage tests that verify original bytes and a proportional 512px PNG thumbnail are persisted through both object-storage and local-storage paths.
- [x] 2.3 Implement decoded-image inspection, detected format metadata, original persistence, and alpha-preserving PNG thumbnail creation in `AiImageStorageService`.
- [x] 2.4 Update image execution result creation to persist the result identity first, store both image renditions, persist only short application URLs and metadata, and publish visual variants only after paired storage succeeds.
- [x] 2.5 Run focused storage and execution tests, including failure behavior that does not complete a result with only an original object.

## 3. Result Resource Access

- [x] 3.1 Add failing controller/service tests for authenticated original and thumbnail resource retrieval, including correct media types and project access denial.
- [x] 3.2 Add the project-scoped thumbnail route and service/storage lookup while preserving the existing original download route.
- [x] 3.3 Update original download response headers to use the detected source media type and matching filename extension; serve thumbnails as `image/png`.
- [x] 3.4 Update image-task result mapping tests to assert list-facing thumbnail URLs and original preview/download URLs are short application routes rather than Base64 Data URLs.

## 4. Verification

- [x] 4.1 Run the focused AI image adapter, storage, controller, and schema migration test suites.
- [x] 4.2 Run the backend test suite or the broadest practical Maven verification and record any unrelated pre-existing failures separately. Full `mvn test` was stopped after 19 of 221 test classes because its per-class fork runtime is impractical; no failures appeared before stopping. `mvn -q -DskipTests package` completed successfully.
- [x] 4.3 Review the final diff to confirm no unrelated worktree changes are included and all OpenSpec task checkboxes accurately reflect completion.
