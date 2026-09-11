## 1. Backend Resource Authorization

- [x] 1.1 Add failing controller integration tests for authenticated original and thumbnail requests without `X-Tenant-Id`, unauthorized access, and project/result mismatch.
- [x] 1.2 Implement result-derived tenant and project permission resolution for the original and thumbnail service operations.
- [x] 1.3 Update the two resource controller endpoints to use the authorized service result without the header-dependent permission aspect or duplicate lookups.
- [x] 1.4 Run the focused backend controller integration suite and confirm all resource authorization cases pass.

## 2. Frontend Rendition Selection

- [x] 2.1 Add failing production-workbench tests proving gallery tiles use thumbnails while the selected large preview uses the original image.
- [x] 2.2 Expose the selected result thumbnail in visual-variant view data and render it in gallery tiles with original-image fallback for legacy results.
- [x] 2.3 Run focused frontend tests and TypeScript checks for the production workbench.

## 3. End-to-End Verification

- [x] 3.1 Run backend regression tests covering AI image storage, routing, and controller behavior.
- [x] 3.2 Run frontend lint/build verification relevant to the changed workbench files.
- [x] 3.3 Verify on the deployed service that an authenticated browser loads both original and thumbnail URLs without HTTP 400 and that unauthorized access remains blocked.
