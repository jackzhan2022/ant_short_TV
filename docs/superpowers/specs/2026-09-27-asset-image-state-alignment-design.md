# Asset Image State Alignment Design

## Problem

AI asset extraction creates visual variants with `generation_status = 'NOT_GENERATED'`, while batch image generation only treats `NOT_STARTED` and `FAILED` as eligible. Extracted variants are therefore classified as unavailable before an image task is created.

During extraction finalization, obsolete AI variants can be retired after new variants have already been inserted. A new variant may be created as non-primary because the old primary still exists at insertion time. When finalization later retires the old primary, no remaining variant is promoted, leaving a character without a primary visual.

## Design

1. New visual variants created by episode asset persistence use the canonical initial state `NOT_STARTED`.
2. Batch generation continues to accept legacy `NOT_GENERATED` rows as eligible. This keeps existing projects usable without a data migration.
3. After automatic variant retirement, each affected asset with active variants but no active primary gets one primary variant. Selection is deterministic: prefer a completed variant with a usable image, then the oldest active variant.
4. Apply the primary repair in both full asset-recognition finalization and scoped asset re-extraction finalization because both paths retire AI variants.
5. Do not rewrite existing generation statuses or restore retired variants. The runtime compatibility rule handles historical rows, while future rows use the canonical state.

## Data Flow

New extraction writes `NOT_STARTED`. Batch preflight classifies `NOT_STARTED`, legacy `NOT_GENERATED`, and `FAILED` as runnable when a prompt exists. Finalization retires stale variants, then promotes a remaining active variant only when the asset has no active primary.

## Error Handling

Primary repair is part of the same transaction as finalization. If it fails, finalization rolls back rather than persisting a partially retired asset graph.

## Tests

- Unit test that `NOT_GENERATED` is classified as `PENDING` for a primary variant.
- Persistence test or source-level assertion that newly extracted variants use `NOT_STARTED`.
- Finalizer tests verify that retirement is followed by a guarded primary promotion for included asset types.
- Scoped re-extraction tests verify the same repair and ensure excluded asset types are untouched.
- Run focused backend tests for image batch classification and both finalization paths.

## Non-Goals

- No production data mutation or deployment.
- No changes to image provider routing, prompts, billing, or task execution.
- No restoration of retired variants.
