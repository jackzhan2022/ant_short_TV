# Script Analysis Operations

## Progress Semantics

- Four stages are reported in fixed order: global understanding, episode splitting, episode summary, character/scene recognition.
- Stage percentages are stage progress, not provider-internal inference progress.
- A stage reaches `100%` only after a valid structured result is persisted.
- Overall progress is derived from the four stage weights and should remain monotonic.

## Episode Split Inputs

- If the script has explicit episode headings, deterministic parsing is the first path.
- If headings are missing or insufficient, the AI split stage uses the full script content plus global understanding and project settings.
- Split output must be sequential, non-empty, and cover the original script text.

## Retry Behavior

- Retry always starts from the selected failed stage.
- Earlier successful stages remain unchanged.
- Later stages are reset to pending and wait for the retried stage to succeed.
- Retry is safe to repeat and should not create duplicate successful earlier results.

## Model Configuration

- All four stages use the project-scoped TEXT model resolution.
- AI calls should record provider request ID, AI call log ID, and duration.
- Sanitized raw responses are retained for debugging and audit.

## Operational Failures

- A failed stage keeps its error code, error message, and retryable flag.
- Old-version analysis results must never overwrite newer script versions.
- Project creation should remain successful even if analysis scheduling fails.
