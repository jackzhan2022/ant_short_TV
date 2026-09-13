# Storyboard Workflow Agent Handoff

## Current status

- OpenSpec progress: 33/40 tasks complete.
- The feature flag remains disabled by default: `AI_WORKFLOW_STORYBOARD_ENABLED=false`.
- Focused backend storyboard/Workflow Agent tests passed individually.
- Frontend storyboard tests passed (13/13), and TypeScript checking passed.
- The full backend suite, complete frontend quality gate, and local end-to-end exercise have not been completed.

## Implemented scope

- Formal episode-scoped storyboard schema and compatibility fields.
- Storyboard planning, material-reference, and Seedance prompt Skills.
- `short-drama-storyboard` Bootstrap, ordered six-tool contract, scope guards, and Adapter.
- Trusted episode/adjacent/global/project/material reads.
- Structured save validation, deterministic prompt/Mention rendering, and atomic episode replacement.
- Episode-scoped asynchronous API and idempotency checks.
- Per-episode generation UI and one rich prompt editor per storyboard.
- Fixed mansion/banquet/equity sample generation was removed.

## Required next work

1. Fix failure accounting in `ScriptAiOperationExecutionHandler` using TDD. If a Workflow Agent performs model calls and later fails during schema/tool/save validation, `WorkflowAgentRunner` throws before returning its result. The catch path currently sees no Agent calls, records no usage/cost, and settles as `PROVIDER_REJECTION` with zero calls. Recover `workflow_agent` call logs for the current execution and attempt, record all calls, update the attempt from the last call, and settle as `PROVIDER_BILLED_FAILURE` using the actual call count.
2. Complete task 6.4 coverage for retryable provider/schema/stale-source/missing-save failures and confirm prior formal storyboards remain unchanged.
3. Complete task 7.2 controller/service coverage for execution status, successful refresh, and failure preservation.
4. Run full backend tests: `cd backend && mvn test`.
5. Run frontend gates: `cd frontend && npm test -- --run`, `npm run tsc`, `npm run lint`, `npx antd lint ./src`, and `npm run build`.
6. Exercise one successful generation and one failed regeneration locally. Verify prompt formatting, Mention metadata, atomic replacement, Run audit, usage, cost, and point settlement.
7. Enable the flag only in the intended environment after verification. Rollback is disabling `AI_WORKFLOW_STORYBOARD_ENABLED`; do not drop schema or audit data.

## Useful focused commands

```powershell
cd backend
mvn -q "-Dtest=StoryboardWorkflowAgentMigrationTest,StoryboardAgentBootstrapTest,StoryboardSkillContractTest,StoryboardToolSchemaTest,StoryboardToolDataServiceTest,StoryboardAgentRunContractTest,StoryboardAgentAdapterTest,WorkflowAgentScopeGuardTest,ScreenplayToolDataServiceTest,ScriptWorkflowControllerTest" test

cd ..\frontend
npm test -- --run src/pages/projects/production-workbench/storyboard.test.tsx
npm run tsc
```

## Files central to the remaining accounting fix

- `backend/src/main/java/com/antshorttv/script/ScriptAiOperationExecutionHandler.java`
- `backend/src/main/java/com/antshorttv/workflowagent/run/WorkflowAgentRunRepository.java`
- `backend/src/main/java/com/antshorttv/script/ScriptAnalysisExecutionService.java` (reference accounting pattern)
- `backend/src/test/java/com/antshorttv/workflowagent/run/WorkflowAgentRunRepositoryTest.java`

Do not edit `frontend/src/services/ant-design-pro/`. Preserve the exact fixed sentence:

`视频中不得出现任何字幕、文字叠加、纯画面，不要bgm，不要配乐。`
