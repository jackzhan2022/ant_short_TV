## Why

The current image adapter submits legacy Image API requests and persists provider Base64 Data URLs directly in result URL columns. The resulting values exceed the database column limits, while list pages also load full-resolution images unnecessarily.

## What Changes

- Replace the XiongXiong image provider's legacy Image API calls with the OpenAI-compatible Responses API and its `image_generation` tool.
- Resolve the top-level Responses model from `responsesModel`, defaulting to `gpt-5.6-terra`, while retaining the selected image model at the tool level.
- Persist each generated original image and a server-generated thumbnail in MinIO instead of persisting Base64 result data.
- Return thumbnail URLs for list rendering and original-image URLs for preview and download.
- Add an authenticated thumbnail resource endpoint for generated image task results.

## Capabilities

### New Capabilities

- `generated-image-storage`: Stores generated originals and thumbnails and exposes the appropriate resource for list, preview, and download use.

### Modified Capabilities

- `ai-invocation-contract`: Extends the unified invocation entrypoint to support Responses API image-generation tool calls and typed image outcomes.

## Impact

- Backend adapter: `XiongXiongAiAdapter`.
- Image execution, storage, response mapping, and result-resource controller paths under `backend/src/main/java/com/antshorttv/aiimage`.
- Existing object storage integration and image-generation tests.
- No frontend request contract or task/point settlement behavior is removed.
