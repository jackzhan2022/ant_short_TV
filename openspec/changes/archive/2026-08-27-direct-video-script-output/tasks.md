## 1. Direct screenplay contract

- [x] 1.1 Add failing normalizer tests for accepting a non-empty `script` JSON response and rejecting missing or blank `script`.
- [x] 1.2 Replace the seven-array `VideoAnalysis` contract with the validated `script` contract while retaining normalized JSON for diagnostics and historical draft processing.
- [x] 1.3 Update normalizer tests and run the focused video-analysis test suite.

## 2. Video decomposition execution

- [x] 2.1 Add an integration test proving a new `PENDING_ANALYSIS` episode writes the direct screenplay to `draft_content`, reaches `PENDING_REVIEW`, and creates exactly one provider call and execution settlement.
- [x] 2.2 Update the successful video-analysis path to save the direct screenplay, increment the draft version, clear the claim, and complete the existing video-analysis execution as the episode result.
- [x] 2.3 Preserve and test the `PENDING_DRAFT` / `DRAFT_GENERATING` legacy draft-generation path for tasks created before the new protocol.
- [x] 2.4 Run focused execution and accounting tests to verify status, attempts, call logs, reservations, and usage records.

## 3. Prompt and regression coverage

- [x] 3.1 Replace the `video-understanding` Agent prompt with the approved professional short-drama screenplay instructions and require only `{"script":"..."}` JSON output.
- [x] 3.2 Add a registry rendering test that verifies the prompt contains the required screenplay sections and the single-field JSON contract.
- [x] 3.3 Run the targeted backend tests and `mvn -q -DskipTests package`.

## 4. Deployment readiness

- [x] 4.1 Review the diff to ensure no frontend draft-edit or confirmation API changes are required.
- [x] 4.2 Deploy only after explicit authorization, preserving the current release's frontend static assets and validating the service health and a non-billed read-only endpoint.
