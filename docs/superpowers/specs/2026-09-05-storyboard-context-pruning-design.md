# Storyboard Context Pruning Design

## Goal

Reduce the model input used to generate one episode's storyboards without weakening source coverage, material binding, or cross-episode continuity. The current production case sends roughly 220,000 serialized characters even though the selected episode contains only 1,238 characters. Most of the excess comes from the `EPISODE_SPLITTING` analysis result embedding about 130,000 characters of full-project script content and from persisted raw model responses being forwarded alongside normalized results.

The production acceptance target is project 26. The optimized flow must retain the complete current episode and its trusted `S0001...` source segments while sending only relevant analysis and compact continuity context to the model.

## Scope

This change affects only the server-prepared context for the `short-drama-storyboard` workflow agent. It does not change the general `read_script_analysis`, `read_adjacent_episodes`, or `read_script_assets` tool contracts used by other agents. It does not change storyboard numbering, save validation, billing, or the one-correction policy.

## Selected Approach

Add a dedicated `StoryboardContextReducer` between trusted tool execution and the storyboard planning model call.

The runner continues to execute and audit the five existing preparation tools in their current order. Their original results remain the evidence attached to tool steps. Before constructing the model message, the runner passes a copy of those results to the reducer and sends only the reduced document to the model.

This localizes behavior to storyboard generation and avoids weakening shared tools or introducing a database snapshot lifecycle.

## Context Policy

The reducer applies this priority order:

1. Complete current episode content and all trusted source segments.
2. Current episode title, number, summary, and other identifiers needed by save validation.
3. Current-episode analysis summary and relevant character, scene, and prop assets.
4. Compact global story understanding needed for tone and continuity.
5. Previous and next episode continuity excerpts.

The current episode is never truncated by the reducer. The overall reduced context has a 30,000-character soft budget. Lower-priority optional material is shortened or omitted when the soft budget is exceeded.

### Current episode

Preserve the complete `read_current_episode` result, including the full text, fingerprint, and every source segment. The reducer must not renumber, omit, or rewrite source segments.

### Adjacent episodes

For each available previous or next episode, retain identifiers, episode number, title, and summary. Replace the complete adjacent screenplay with bounded beginning and ending excerpts. Excerpts must be produced on Unicode code-point boundaries and carry explicit truncation metadata so the model does not mistake them for complete scripts.

### Script analysis

Do not forward task status, stage execution metadata, `raw_response`, or full episode-splitting content.

Retain a compact projection of global understanding: logline, themes, genres, world setting, core conflict, narrative style, relationships, turning points, ending hook, and other small structured fields already present in the normalized global document.

From episode-oriented normalized results, retain only the entry matching the selected episode number and only summary-scale fields. Explicitly exclude any `content`, `script`, `screenplay`, raw-response, or full-text field regardless of nesting.

### Project context and assets

Keep the compact project context needed for visual and narrative style.

Select assets whose names or stable identifiers occur in the current episode content or its structured source segments. Preserve essential identity and binding fields while omitting verbose generated descriptions that are not required for storyboard material references. If no asset can be matched reliably, fall back to a compact identity-only list rather than returning an empty collection.

## Size Enforcement

The reducer measures serialized UTF-16 character counts before and after reduction and records both values in structured application logs with the workflow run ID and episode ID.

The 30,000-character value is a soft budget because the current episode must remain complete. If required current-episode data alone exceeds the budget, the reducer keeps it and removes all optional context. It must never truncate source segments to meet the budget.

Optional sections are reduced in reverse priority order: adjacent excerpts, optional global fields, fallback asset entries, then nonessential project metadata.

## Error Handling

Malformed or unexpectedly shaped optional analysis data must not fail storyboard generation. The reducer drops the malformed optional section, logs a structured warning without screenplay content, and continues with the trusted current episode, compact project context, and available asset identities.

Failures in the existing trusted preparation tools remain terminal as they are today. Save validation and deterministic correction behavior are unchanged.

## Observability

For each storyboard run, log:

- original prepared-context character count;
- reduced prepared-context character count;
- retained adjacent episode count;
- retained character, scene, and prop asset counts;
- whether optional sections were dropped to honor the soft budget.

Logs must contain counts and identifiers only, not screenplay or prompt content.

## Testing

Use test-driven development for the reducer and runner integration.

Unit tests must prove that:

- full-project episode-splitting content and every raw response are absent;
- only the selected episode summary is retained;
- adjacent episode bodies become bounded beginning and ending excerpts;
- current episode text and every source segment remain byte-for-byte equivalent;
- matching assets are retained and unmatched verbose data is removed;
- the identity-only fallback prevents accidental empty material context;
- malformed optional analysis degrades safely;
- the soft budget removes optional sections before required current-episode data;
- size metrics are produced without logging content.

Runner tests must prove that the five trusted reads are still executed and audited before one planning call, while the planning message receives the reduced document rather than the original tool payloads.

## Rollout and Acceptance

Deploy through the existing release and rollback process. After deployment, generate storyboards for project 26 and compare the new execution with successful execution 91399.

Acceptance requires:

- successful atomic save with valid source-segment coverage;
- no additional model retry caused by pruning;
- complete material bindings for generated storyboards;
- materially smaller planning input, with a target below 30,000 characters for project 26;
- recorded model duration and total execution duration for before-and-after comparison;
- no regression in the shared backend and frontend verification suites.
