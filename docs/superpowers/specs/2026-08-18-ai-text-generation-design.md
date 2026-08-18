# AI Text Generation Integration Design

## Context

The current AI service management module can store tenant-scoped TEXT service
configs and test connectivity against external providers. The project text
workflow still generates scripts, rewrites, extracted assets, storyboards, and
prompts from local templates or fixed data. This design connects those business
workflows to real text model calls.

The standalone frontend chatbot page is out of scope for this change. It uses a
demo endpoint and does not represent the platform text production workflow.

## Goals

- Route script generation, script rewrite, asset extraction, storyboard
  breakdown, and prompt generation through the backend AI service layer.
- Support OpenAI-compatible chat completions and Gemini generateContent for the
  first production integration.
- Keep tenant isolation, project permission checks, and AI call logging.
- Fail clearly when no text service is configured, the provider returns an
  error, or structured output cannot be parsed.

## Non-Goals

- No streaming responses.
- No async text job queue.
- No retry, fallback, quota, or cost accounting.
- No changes to image, video, voice, or single-shot compose workflows.
- No migration of the frontend chatbot page.

## Architecture

Add a backend service, `AiTextGenerationService`, responsible for:

1. Selecting the active tenant TEXT service config, preferring default config and
   then highest priority.
2. Building the provider request:
   - OpenAI-compatible: `POST {baseUrl}/{endpoint or /chat/completions}` with
     `model`, `messages`, and a conservative token budget.
   - Gemini: `POST {baseUrl}/{endpoint or /v1beta/models/{model}:generateContent}`
     with `contents.parts.text`.
3. Sending the request with the decrypted API key.
4. Parsing model text from known response shapes.
5. Writing `ai_call_log` with success or failure details.

`ScriptWorkflowService` will delegate all AI-labeled workflow operations to this
new service. It remains responsible for authorization, project access, database
updates, and converting generated content into domain records.

## Data Flow

1. Frontend calls the existing project text workflow endpoint.
2. Backend validates tenant, project, and permission.
3. `ScriptWorkflowService` builds a workflow-specific prompt.
4. `AiTextGenerationService` resolves and calls the tenant TEXT model.
5. Plain text responses are stored directly for script generation and rewrite.
6. Structured workflows ask the model for JSON and parse it before writing:
   - `extractElements`: characters, scenes, and props.
   - `breakdownStoryboards`: storyboard rows.
   - `generatePrompts`: prompt values for existing assets and storyboards.

## Error Handling

- Missing enabled TEXT config returns the existing validation-style business
  error.
- HTTP non-2xx responses return a business error that includes status code and a
  short response summary.
- Network failures return a business error with the provider failure reason.
- Empty model output returns a business error.
- Invalid JSON in structured workflows returns a business error explaining that
  the model output format is invalid.
- Failures are recorded in `ai_call_log` with status `FAILED`.

## Provider Detection

Provider selection is intentionally simple:

- `provider` equal to `Gemini`, case-insensitive, uses Gemini protocol.
- All other providers use OpenAI-compatible chat completions.

This matches the current service configuration model and leaves more provider
protocols for later iterations.

## Testing

Backend tests will use a local fake HTTP server so tests verify real outbound
HTTP behavior without calling external vendors.

Required coverage:

- OpenAI-compatible request body and response parsing.
- Gemini request body and response parsing.
- Missing TEXT config fails before HTTP call.
- HTTP non-2xx response records failed call log and raises an error.
- Script generation stores model text, not local template text.
- Structured JSON parse failure raises a clear validation error.

Existing workflow controller tests should continue to cover endpoint-level
permission and persistence behavior.
