# Responses Image Generation and Storage Design

## Goal

Replace the current image generation requests to the XiongXiong AI gateway with
the OpenAI-compatible Responses API image-generation tool flow. Persist generated
images as object storage files rather than Data URLs, and provide a thumbnail for
list rendering.

## Scope

This change applies to completed AI image task results. It does not change task
claiming, idempotency, retry policy, point reservation or settlement.

## Request Contract

`XiongXiongAiAdapter` will submit `POST /v1/responses`.

The request uses:

- The top-level `model` from the routed model configuration's `responsesModel`.
  When it is absent, the default is `gpt-5.6-terra`.
- An `image_generation` tool. Its `model` is the selected image model, such as
  `gpt-image-2`; it remains independent from the top-level Responses model.
- One `input_text` content item containing the asset prompt.
- One `input_image` Data URL per supplied reference image.
- `action: generate` with no reference images and `action: edit` when references
  are supplied.

The adapter reads the Base64 image from the `output` entry whose type is
`image_generation_call`. It honors an emitted output format when it is available;
otherwise, the storage layer detects the decoded image format.

There is no fallback to `/v1/images/generations` or `/v1/images/edits`.

## Storage and Result Data

Base64 data never enters `ai_image_result.image_url` or
`ai_image_result.thumbnail_url`.

For each generated result, the backend will:

1. Decode the image bytes and inspect their dimensions and MIME type.
2. Upload the original bytes to MinIO through the existing object storage service.
3. Create an aspect-ratio-preserving PNG thumbnail with a maximum edge of 512
   pixels, preserving alpha where the source has it, and upload it to MinIO.
4. Persist the original storage path, original dimensions and byte size.
5. Persist short authenticated application URLs for the original and thumbnail.

The two object keys live together under the generated-image material path, with
separate `original.<extension>` and `thumbnail.png` objects. The result row is
only finalized after both uploads succeed. A storage failure leaves no completed
result and is handled by the existing task retry/failure path.

## Read Contract

- List and task-result responses expose `thumbnailUrl` for image list rendering.
- `imageUrl` points to the original image.
- The existing download endpoint continues to stream the original from
  `storage_path`.
- A new authenticated thumbnail endpoint streams the corresponding thumbnail.

The controller supplies the actual image MIME type and a matching download file
extension, rather than assuming every stored original is PNG. The generated
thumbnail is always served as `image/png`.

## Compatibility

Existing placeholder images retain their current storage behavior. Existing result
records remain readable through their recorded original path and URL fields; the
thumbnail endpoint applies to results created by this implementation.

## Tests

Tests will first demonstrate the intended behavior, then the implementation will
make them pass:

- The adapter posts the Responses payload with the configured/default top-level
  model, image tool model, and correct generate/edit behavior.
- The adapter extracts an `image_generation_call` Base64 result.
- Storage writes original and thumbnail objects, preserves original metadata, and
  returns streamable resources for both.
- Completed task responses contain short application URLs rather than Data URLs.
- Original and thumbnail endpoints stream the respective media types.

## Success Criteria

- No generated Base64 value is persisted to an image-result URL column.
- Every completed generated image has both an original and a thumbnail in MinIO.
- Lists can render from the thumbnail URL while preview and download retrieve the
  original.
- Image generation uses only `/v1/responses` with `image_generation`.
