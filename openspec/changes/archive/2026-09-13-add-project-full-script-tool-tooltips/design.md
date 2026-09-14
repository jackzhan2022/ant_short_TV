## Context

The workflow Agent tool catalog currently exposes episode-scoped screenplay reads and includes a human-readable description in every tool definition. Administrators need a whole-project read for cross-episode work, while the Agent editor currently drops the existing descriptions from its tool-selection interactions.

The change spans the Spring tool registry/data service and the React Agent editor. It does not require a database migration or a new permission because tools already execute inside an authorization-scoped workflow run.

## Goals / Non-Goals

**Goals:**

- Return all current, valid project episodes in deterministic episode order without discarding episode boundaries.
- Keep project scoping server-controlled and make the new tool read-only.
- Make the tool catalog's description visible at every Agent-editor interaction where a tool is selected, reviewed, or inserted into a prompt.

**Non-Goals:**

- Do not add model-supplied project IDs, pagination, episode-range parameters, or a concatenated single-string script response.
- Do not change existing episode, adjacent-episode, validation, or save tool contracts.
- Do not create a visual workflow editor, new Agent/Skill versioning, or a database schema migration.

## Decisions

### Return structured episode records

`read_project_full_script` will return `{ episodes: [...] }`, with the same episode metadata and `content` used by single-episode reads. This keeps scene and episode boundaries machine-readable. A joined text response was rejected because it makes subsequent model processing ambiguous and prevents callers from reliably identifying an episode.

### Reuse the current-version selector and scoped project lookup

The tool data service will reuse the existing current-episode selection path, filtered by the run context's project. This keeps read semantics consistent with `read_episode_script`, prevents caller-controlled scope escalation, and avoids a parallel script-version query.

### Reuse catalog descriptions for hover help

The Agent editor will render the existing `WorkflowTool.description` in Ant Design tooltips for option labels, selected values, and prompt insertion buttons. A permanently expanded description was rejected because it enlarges a multi-select catalog; a separate details panel was rejected because it adds an unnecessary action before selection.

## Risks / Trade-offs

- [Large scripts can consume a model context window] → Preserve records rather than concatenate them; the Agent can select episode-scoped tools when full-project context is unnecessary.
- [A project can have no valid episodes] → Return an empty `episodes` array, matching normal list semantics.
- [Customized Select rendering can regress selection accessibility] → Test option, selected-tag, and insertion-button descriptions with accessible tooltip triggers.

## Migration Plan

1. Deploy the backend and frontend together; no data migration is required.
2. Existing Agents remain unchanged because the new tool is not implicitly granted.
3. Administrators explicitly add the tool to an Agent before it can be invoked.
4. Rollback by deploying the preceding application release; persisted Agent records do not contain the new tool unless an administrator has saved it, in which case the older runtime will reject that unknown tool and the Agent can be edited to remove it.

## Open Questions

None.
