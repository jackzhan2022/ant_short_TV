## Context

Generated image tasks currently call legacy `/v1/images/generations` or `/v1/images/edits` endpoints through `XiongXiongAiAdapter`. The adapter transforms Base64 output into a Data URL, and the execution handler writes that value into URL fields designed for short links. This causes database truncation and makes list views retrieve full-resolution image data.

The project already has typed invocation routing, durable image-task execution, authenticated result downloads, and object storage services. The design must preserve those reliability and billing boundaries while replacing only the provider request, response normalization, and generated-image persistence path.

## Goals / Non-Goals

**Goals:**

- Call `/v1/responses` with the `image_generation` tool for all new XiongXiong image generations and edits.
- Resolve the Responses orchestration model from `responsesModel`, defaulting to `gpt-5.6-terra`; keep the selected image model as the tool model.
- Store an original and a 512px-maximum-edge PNG thumbnail in MinIO for every completed generated image.
- Expose thumbnails for list views and originals for preview and download without persisting Data URLs.
- Preserve task claiming, retries, idempotency, authorization, and point settlement.

**Non-Goals:**

- Migrating existing image-result records or regenerating historical thumbnails.
- Changing image prompts, model pricing, provider failover, or frontend task-creation APIs.
- Adding image transformations beyond proportional thumbnail resizing.

## Decisions

### Use Responses API with an explicit tool model

The adapter will submit the prompt and optional reference-image Data URLs as Responses input, and declare an `image_generation` tool. It will use `responsesModel` for the top-level model with `gpt-5.6-terra` as fallback, and the routed image model in the tool declaration. With references, the action is `edit`; otherwise it is `generate`.

This separates orchestration from image rendering and matches the gateway flow used by xshop. Retaining the Image API would be less invasive but preserves the incompatible response shape and does not meet the requested migration. Inferring the image model from the top-level model is rejected because it mixes two independently configurable responsibilities.

### Normalize output as bytes before result persistence

The adapter will find the `image_generation_call` entry in `output` and return its Base64 result and format metadata as typed image output. The execution handler will create a result identity, then pass decoded bytes to the image storage service. Only storage paths, dimensions, sizes, and short application URLs are persisted.

Passing the Base64 Data URL further downstream is rejected because it directly causes the `varchar` overflow and couples persistence to provider encoding.

### Store paired original and thumbnail objects

For each generated result, storage will write `original.<detected-extension>` and `thumbnail.png` under a common material path. The thumbnail is created server-side using proportional scaling with a maximum source edge of 512px. PNG preserves alpha without introducing another image-codec dependency.

Pre-generating the thumbnail trades one CPU resize and one extra object for faster list rendering and predictable small payloads. Generating it on read is rejected because it adds runtime latency and repeats work. Replacing the original with a thumbnail is rejected because preview and downloads require full fidelity.

### Use authenticated application resource routes

The existing original download route will stream the original based on the recorded original storage path. A thumbnail route with the same project/result authorization will stream the thumbnail. Result responses expose stable relative URLs to these routes rather than direct or expiring MinIO URLs.

Direct MinIO URLs are rejected because the existing application route is the authorization boundary. The original route will use detected media type and extension; thumbnails always use `image/png`.

## Risks / Trade-offs

- [Gateway Responses implementation differs from the documented tool response] -> Parse only the typed image-generation call and map missing/invalid output to the existing normalized invalid-response failure.
- [A source image cannot be decoded or thumbnail creation fails] -> Treat storage as failed, avoid publishing a completed result, and allow the existing execution retry policy to handle it.
- [Original succeeds but thumbnail upload fails] -> Do not finalize the result; leave cleanup to task/result cleanup and log the failed attempt for operators.
- [PNG thumbnails are larger than an optimized WebP equivalent] -> Keep PNG for transparency and standard Java compatibility; revisit only when list image telemetry justifies an additional codec dependency.
- [Historical records do not have thumbnail objects] -> Preserve their original resource behavior; the thumbnail route applies only to new paired results.

## Migration Plan

1. Deploy schema-compatible code that writes short URLs and paired storage objects for newly completed results.
2. Verify an image generation task uses `/v1/responses`, both objects exist in MinIO, list responses contain thumbnail URLs, and preview/download return originals.
3. Monitor invocation logs and failed task reasons after release.
4. Roll back by redeploying the prior adapter and execution implementation. New records remain valid because original storage paths and application URLs are retained; unfinished tasks can use the existing retry behavior after rollback.

## Open Questions

None. The thumbnail size, format, model default, routing behavior, and no-fallback policy are confirmed.
