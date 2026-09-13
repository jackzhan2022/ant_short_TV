## 1. Static Tab Interaction

- [x] 1.1 Replace URL-derived model-management tab state with local state initialized from the first permitted tab.
- [x] 1.2 Remove query-string parsing and history updates from model-management tab selection.

## 2. Legacy Route Compatibility

- [x] 2.1 Update legacy provider, model, billing, and call-log redirects to the model-management landing route without tab query parameters.

## 3. Verification

- [x] 3.1 Update model-management component tests for immediate local tab switching and permission-filtered visible tabs.
- [x] 3.2 Update route tests for legacy redirects without query parameters and run the focused frontend test suite.
