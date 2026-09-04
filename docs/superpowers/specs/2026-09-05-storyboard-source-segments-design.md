# Storyboard Source Segments Design

## Problem

The production storyboard Agent currently spends one model round trip choosing each of five deterministic read tools, then asks the model to reproduce source start and end marker text for every storyboard. Live execution `89665` took 329 seconds and exhausted three attempts because copied markers were missing, ambiguous, or did not form a gap-free boundary chain. Read tools and database validation completed in under one second; model round trips and full-attempt retries consumed nearly all elapsed time.

## Confirmed design

### Deterministic source segments

The server converts the current episode into ordered source segments before the model call. Every non-blank, storyboard-relevant physical line receives an episode-local ID from `S0001` upward. Episode/document metadata is exposed as context but has `requiredCoverage=false`; scene headings, action, dialogue, narration, and inner OS have `requiredCoverage=true`.

Each segment contains:

- `id`: episode-local ordinal such as `S0007`;
- `type`: `SCENE`, `ACTION`, `DIALOGUE`, `NARRATION`, or `INNER_OS`;
- `text`: exact source text;
- `startOffset` and `endOffset`: server-only offsets into the trusted episode source;
- `requiredCoverage`: whether the segment must belong to a storyboard.

IDs are valid only together with the episode ID and `episodeFingerprint`. Editing the episode changes the fingerprint and invalidates an in-flight result. Version 1 does not split one physical non-blank line into smaller segments.

### One prepared context

The execution host invokes the existing five read tools itself, in the existing trusted execution scope, and records each invocation in the Agent Run audit. It then sends their combined results to the model in one planning request. The model does not spend separate turns deciding an already fixed read order.

The prepared context contains the segmented current episode, adjacent summaries, formal script analysis, project visual style, and bounded script assets. Old storyboards remain excluded.

### Structured model result

Each storyboard replaces copied `sourceStartMarker` and `sourceEndMarker` values with:

```json
{
  "storyboardNo": 1,
  "sourceFrom": "S0001",
  "sourceTo": "S0004",
  "shots": []
}
```

Storyboard ranges must be ordered, contiguous across all required segments, non-overlapping, and collectively exhaustive. The server resolves ranges back to trusted source text by stored offsets. Spoken content is identified by segment ID and injected from trusted source text; the model does not reproduce authoritative dialogue, narration, or inner OS text.

### Validation and persistence

The server validates the complete structured result before mutation. Invalid segment IDs, gaps, overlaps, reordered ranges, a stale fingerprint, invalid durations, or invalid assets reject the save. A valid set continues to use the existing one-transaction episode replacement and deterministic prompt renderer.

Validation failures return structured diagnostics such as:

```json
{
  "code": "SOURCE_SEGMENT_GAP",
  "storyboardNo": 3,
  "expectedSegmentId": "S0018",
  "actualSegmentId": "S0020"
}
```

### Retry behavior

Provider transport failures remain eligible for an execution-attempt retry. Deterministic scope, stale-source, schema, segment-coverage, duration, and asset-validation failures do not restart all reads and regenerate the whole episode. A correctable save validation may receive one targeted correction turn in the same Run; repeating the same validation code ends the attempt with the structured diagnostic.

## Compatibility

- No database column is required for segment IDs in version 1; authoritative `shot_plan_json` stores the submitted range IDs and the existing source fingerprint binds them to the episode version.
- Existing storyboards and historical media remain readable.
- The feature flag remains the rollback boundary.
- The frontend keeps one editable prompt per formal storyboard; no new confirmation UI is required.

## Verification

- Unit tests prove deterministic segmentation, metadata exclusion, exact text/offset preservation, and fingerprint invalidation.
- Save tests prove complete ranges succeed and gaps, overlap, order errors, unknown IDs, and stale fingerprints preserve prior rows.
- Runner tests prove five reads are host-driven, audited, and combined into one planning request.
- Retry tests prove deterministic validation does not create a fresh full attempt while transport failures still retry.
- A production smoke test must complete one real episode generation and verify Run steps, prompt documents, material Mentions, point settlement, and atomic replacement.

## Explicit non-goals

- No semantic paragraph splitter or model-generated segment IDs.
- No character-offset counting by the model.
- No cross-episode or selected-text generation.
- No Toonflow-style client-owned XML persistence.
