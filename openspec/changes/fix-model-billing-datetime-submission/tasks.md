## 1. Regression Tests

- [x] 1.1 Add a cost-price publication test that submits picker-formatted start and end times and expects ISO local date-time values in the generated client payload.
- [x] 1.2 Add a point-price publication test that expects the same normalization and verifies an already-normalized start time is unchanged.
- [x] 1.3 Run the focused model billing test file and confirm the new assertions fail because the page still forwards space-separated values.

## 2. Frontend Fix

- [x] 2.1 Add a page-local date-time normalizer that changes only the separator between the date and time, preserves suffixes, and accepts an absent optional value.
- [x] 2.2 Apply the normalizer to `effectiveFrom` and `effectiveTo` for both cost-price and point-price publication payloads without editing generated services.
- [x] 2.3 Run the focused model billing tests and confirm both publication paths pass.

## 3. Verification

- [x] 3.1 Run the relevant frontend test suite and TypeScript type checking.
- [x] 3.2 Run Biome and Ant Design lint checks for the affected frontend code.
- [x] 3.3 Run the backend platform AI accounting controller test to retain evidence that ISO local date-times support publish, query, and revoke behavior.

## 4. Online Regression

- [ ] 4.1 Deploy the verified frontend bundle to the internal-test environment. BLOCKED: repository has no deployment channel for `antv.aixmax.cn` and no server credentials are available.
- [ ] 4.2 Publish far-future cost and point price versions for the selected internal test model and verify both appear with pending lifecycle state. BLOCKED by 4.1.
- [ ] 4.3 Revoke both test versions and verify they remain visible as revoked with no further revoke action available. BLOCKED by 4.2.
