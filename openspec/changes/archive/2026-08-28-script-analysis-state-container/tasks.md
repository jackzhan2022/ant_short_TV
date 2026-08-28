## 1. State Container

- [x] 1.1 Extract a reusable ScriptAnalysisStateContainer for pending, running, retrying, and failed states.
- [x] 1.2 Render the top skeleton plus current-episode parsing message, guidance, and four stage progress cards.
- [x] 1.3 Gate the existing script workbench so it mounts only after analysis completes (while preserving legacy no-analysis behavior).

## 2. Behavior and Tests

- [x] 2.1 Preserve polling and retry callbacks through the container without creating duplicate analysis tasks.
- [x] 2.2 Add tests for in-progress isolation, completed workbench rendering, and failed retry state.
- [x] 2.3 Run focused tests, TypeScript/lint checks, and production build.
