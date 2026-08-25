# AI workflow inventory

This inventory defines whether a production workflow crosses the AI provider boundary. A workflow is billable AI only when it contacts a model through `AiInvocationService`; local media processing, remote file transfer, and administrative diagnostics are not AI executions.

| Workflow | Classification | Execution and accounting behavior |
| --- | --- | --- |
| Image generation | Provider AI | Optional `modelId`; explicit, project, then platform-default resolution; durable shared execution, invocation, usage/cost, and point reservation/settlement |
| Script generation, rewrite, extraction, storyboard breakdown, and prompt generation | Provider AI | Durable shared execution through registered script handlers |
| Script analysis and episode-summary fan-out | Provider AI | Shared execution header and attempts while retaining analysis domain records |
| Script review | Provider AI | Shared execution and attempts while retaining review task/issue records |
| AI video generation and provider polling | Provider AI | Optional `modelId`; explicit, project, then platform-default resolution; shared invocation/attempt/usage/cost/settlement records with provider-native asynchronous polling |
| Video decomposition analysis and draft generation | Provider AI | One shared execution header across both domain phases |
| Shot voice placeholder | Local placeholder, not provider AI | Writes a deterministic local placeholder MP3. It does not create AI execution/accounting records or consume points. A real TTS integration must first add a registered execution handler and provider adapter. |
| Shot composition | Local media processing | Writes a local composed media artifact and retains the existing shot compose task/result contract. No AI accounting. |
| Episode composition | Local media processing | Uses JCodec, local/object storage, and existing episode compose/version records. No AI accounting. |
| Inspiration import and media download | Remote data transfer | HTTP is used to import or download source media, not to invoke a model. No AI accounting. |
| Platform Provider connectivity test | Administrative diagnostic | Platform-admin-only credential/endpoint/model validation. It is synchronous and does not create a user-facing execution or bill points. |

## Configuration ownership

Platform Provider credentials, Models, and Model Capabilities are the only provider configuration source. Tenant users may execute workflows with `AI_SERVICE:USE` and inspect tenant-scoped logs with `AI_CALL_LOG:VIEW`; neither permission grants Provider or Model management access.

The parameterless-context overloads in script analysis and review are internal compatibility paths. Controllers and registered handlers use execution-aware overloads, so normal user traffic reserves and settles points through the shared execution.

## Rule for new workflows

Any new user-facing operation that contacts a model must persist a shared execution before provider contact, run through a registered `AiExecutionHandler` and `AiInvocationService`, record normalized usage and cost status, and reserve/settle points under a versioned policy. Local-only work must be named and documented as local and must not call `consumeForAi`.
