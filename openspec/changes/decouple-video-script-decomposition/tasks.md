## 1. Data and Contract Updates

- [x] 1.1 Add backend regression tests that prove upload and batch creation no longer require `projectId`, while confirmation still requires a target project.
- [x] 1.2 Add a migration and persistence coverage for nullable decomposition batch/episode project linkage and tenant-level storage paths.

## 2. Backend Workflow

- [x] 2.1 Remove `projectId` from the upload endpoint and batch creation request contract.
- [x] 2.2 Update decomposition storage, validation, and list/detail access to support tenant-level batches while preserving historical project-bound records.
- [x] 2.3 Keep confirmation/import project-bound and bind unbound decomposition records to the chosen project.

## 3. Frontend Experience

- [x] 3.1 Remove project selection from the video decomposition page's upload/create form and update the batch creation request.
- [x] 3.2 Add clearer step/progress feedback for upload, analysis, draft review, and confirmation.
- [x] 3.3 Improve request error handling so no-response upload failures surface a clear service-unavailable message.

## 4. Verification

- [x] 4.1 Run backend tests covering the updated decomposition workflow.
- [x] 4.2 Run frontend tests plus lint/type-check for the video decomposition page and request error handling.
- [x] 4.3 Summarize any remaining backend-startup or environment issue if the upload path still cannot be exercised end-to-end.
